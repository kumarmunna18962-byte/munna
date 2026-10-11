const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, apikey, x-admin-token",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};
const ADMIN_TOKEN_SHA256 = "40ea7c0b185bd11ce61a6ec515d437c89116935ec78ed341398569da7d0e0627";
const PLANS = {
  monthly: { amount_paise: 2900, days: 30 },
  yearly: { amount_paise: 19900, days: 365 },
};
const encoder = new TextEncoder();

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}
function normalizeEmail(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const email = value.trim().toLowerCase();
  if (email.length < 5 || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return null;
  return email;
}
function normalizeDeviceId(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const id = value.trim().toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(id) ? id : null;
}
async function sha256Hex(value: string): Promise<string> {
  const hash = await crypto.subtle.digest("SHA-256", encoder.encode(value));
  return Array.from(new Uint8Array(hash)).map(v => v.toString(16).padStart(2, "0")).join("");
}
async function isAdmin(req: Request): Promise<boolean> {
  const token = req.headers.get("x-admin-token") || "";
  if (token.length < 32 || token.length > 256) return false;
  const candidate = await sha256Hex(token.trim());
  let diff = candidate.length ^ ADMIN_TOKEN_SHA256.length;
  for (let i = 0; i < Math.max(candidate.length, ADMIN_TOKEN_SHA256.length); i++) {
    diff |= (candidate.charCodeAt(i) || 0) ^ (ADMIN_TOKEN_SHA256.charCodeAt(i) || 0);
  }
  return diff === 0;
}
function envValue(name: string): string | null {
  const v = Deno.env.get(name);
  if (v && v.trim()) return v.trim();
  return null;
}
function getServiceKey(): string | null {
  const old = envValue("SUPABASE_SERVICE_ROLE_KEY");
  if (old) return old;
  const secretJson = envValue("SUPABASE_SECRET_KEYS");
  if (secretJson) {
    try {
      const keys = JSON.parse(secretJson);
      if (typeof keys.default === "string" && keys.default) return keys.default;
    } catch { /* try legacy secret below */ }
  }
  const legacy = envValue("SUPABASE_SERVICE_KEY");
  return legacy;
}
const supabaseUrl = envValue("SUPABASE_URL") || "https://uxrbxpykdxhxvlrwelzo.supabase.co";
const serviceKey = getServiceKey();

async function rest(path: string, init: RequestInit = {}): Promise<{ response: Response; data: any }> {
  if (!serviceKey) throw new Error("VIP backend server key is not configured");
  const headers = new Headers(init.headers || {});
  headers.set("apikey", serviceKey);
  headers.set("Authorization", "Bearer " + serviceKey);
  headers.set("Content-Type", "application/json");
  if (init.method && init.method !== "GET") headers.set("Prefer", "return=representation");
  const r = await fetch(supabaseUrl + "/rest/v1/" + path, { ...init, headers });
  const txt = await r.text();
  let data: any = null;
  try { data = txt ? JSON.parse(txt) : null; } catch { data = { raw: txt.slice(0, 400) }; }
  return { response: r, data };
}
async function readJson(req: Request): Promise<any> {
  const length = Number(req.headers.get("content-length") || 0);
  if (length > 8000) throw new Error("Request too large");
  try { return await req.json(); } catch { throw new Error("Invalid JSON body"); }
}
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return response({ error: "Use POST." }, 405);
  try {
    const body = await readJson(req);
    const action = body?.action;
    if (typeof action !== "string") return response({ error: "Action is required." }, 400);

    if (action === "status") {
      const device = normalizeDeviceId(body.device_id);
      if (!device) return response({ active: false, error: "Device ID is invalid." }, 400);
      const installHash = await sha256Hex(device);
      const qs = new URLSearchParams({ select: "email,plan,status,expires_at", install_hash: "eq." + installHash, limit: "1" });
      const result = await rest("dc_calc_vip_memberships?" + qs.toString());
      if (!result.response.ok) return response({ error: "Could not check membership." }, 502);
      const item = Array.isArray(result.data) ? result.data[0] : null;
      const active = Boolean(item && item.status === "active" && Date.parse(item.expires_at) > Date.now());
      return response({ active, plan: active ? item.plan : null, expires_at: active ? item.expires_at : null });
    }

    if (action === "submit") {
      const device = normalizeDeviceId(body.device_id);
      const email = normalizeEmail(body.email);
      const plan = typeof body.plan === "string" ? body.plan : "";
      const transactionReference = typeof body.payment_reference === "string" ? body.payment_reference.trim() : "";
      if (!device || !email || !(plan in PLANS)) return response({ error: "Enter a valid email and choose a plan." }, 400);
      if (transactionReference.length < 6 || transactionReference.length > 64 || !/^[a-zA-Z0-9 -]+$/.test(transactionReference)) {
        return response({ error: "Enter the transaction/UTR reference (6–64 letters or numbers)." }, 400);
      }
      const installHash = await sha256Hex(device);
      const since = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
      const rateQ = new URLSearchParams({
        select: "id", install_hash: "eq." + installHash, status: "eq.pending",
        created_at: "gte." + since, limit: "3"
      });
      const rate = await rest("dc_calc_vip_requests?" + rateQ.toString());
      if (!rate.response.ok) return response({ error: "Could not submit the request safely. Try later." }, 502);
      if (Array.isArray(rate.data) && rate.data.length >= 3) return response({ error: "Too many pending requests on this device. Please wait for review." }, 429);
      const planInfo = PLANS[plan as keyof typeof PLANS];
      const insert = await rest("dc_calc_vip_requests", {
        method: "POST",
        body: JSON.stringify({
          install_hash: installHash, email, plan, amount_paise: planInfo.amount_paise,
          payment_reference: transactionReference, status: "pending"
        })
      });
      if (!insert.response.ok) {
        if (insert.response.status === 409 || insert.response.status === 400) {
          return response({ error: "That transaction reference was already submitted or could not be accepted. Check it and try again." }, 409);
        }
        return response({ error: "Membership request could not be saved. Please try again later." }, 502);
      }
      return response({ ok: true, status: "pending", request_id: insert.data?.[0]?.id || null, message: "Request submitted. VIP activates only after payment is checked by the developer." }, 201);
    }

    if (action === "admin_list" || action === "approve" || action === "reject") {
      if (!(await isAdmin(req))) return response({ error: "Admin token is invalid." }, 401);
      if (action === "admin_list") {
        const q = new URLSearchParams({
          select: "id,email,plan,amount_paise,payment_reference,status,created_at,vip_until",
          status: "eq.pending", order: "created_at.asc", limit: "100"
        });
        const result = await rest("dc_calc_vip_requests?" + q.toString());
        if (!result.response.ok) return response({ error: "Could not load requests." }, 502);
        return response({ requests: result.data || [] });
      }
      const requestId = typeof body.request_id === "string" ? body.request_id : "";
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(requestId)) {
        return response({ error: "Request ID is invalid." }, 400);
      }
      if (action === "approve") {
        const rpc = await rest("rpc/dc_calc_vip_approve_request", {
          method: "POST", body: JSON.stringify({ p_request_id: requestId })
        });
        if (!rpc.response.ok) return response({ error: "Approval failed: the request may already be reviewed." }, 409);
        const approved = Array.isArray(rpc.data) ? rpc.data[0] : rpc.data;
        return response({ ok: true, approved: true, email: approved?.email || null, plan: approved?.plan || null, expires_at: approved?.expires_at || null });
      }
      const patch = await rest("dc_calc_vip_requests?id=eq." + encodeURIComponent(requestId) + "&status=eq.pending", {
        method: "PATCH", body: JSON.stringify({ status: "rejected", reviewed_at: new Date().toISOString() })
      });
      if (!patch.response.ok || !Array.isArray(patch.data) || patch.data.length === 0) return response({ error: "Request not found or already reviewed." }, 409);
      return response({ ok: true, rejected: true });
    }

    return response({ error: "Unknown action." }, 400);
  } catch (error) {
    console.error("VIP request failed", error instanceof Error ? error.message : "unknown");
    return response({ error: "VIP service temporarily unavailable." }, 500);
  }
});
