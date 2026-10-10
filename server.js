import express from 'express';
import http from 'node:http';
import { Server } from 'socket.io';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import nodemailer from 'nodemailer';

const app = express();
const server = http.createServer(app);
const io = new Server(server);
const PORT = process.env.PORT || 3000;
const SECRET = process.env.DC_CHAT_SECRET;
const ADMIN_EMAIL = String(process.env.DC_CHAT_ADMIN_EMAIL || '').trim().toLowerCase();
if (process.env.NODE_ENV === 'production' && (!SECRET || SECRET.length < 32)) {
  console.error('Set DC_CHAT_SECRET to a random secret of at least 32 characters.'); process.exit(1);
}
if (process.env.NODE_ENV === 'production' && ADMIN_EMAIL && !/^[^\s@]+@gmail\.com$/.test(ADMIN_EMAIL)) {
  console.error('DC_CHAT_ADMIN_EMAIL must be a valid Gmail address when provided.');
  process.exit(1);
}
if (process.env.NODE_ENV === 'production' && !ADMIN_EMAIL) {
  console.warn('DC_CHAT_ADMIN_EMAIL is not set. Service will start, but owner admin features stay disabled until it is configured.');
}
const JWT_SECRET = SECRET || 'local-development-only-change-before-deploy-123456';
const DB = process.env.DC_CHAT_DB || path.resolve('dc-chat-data.json');
const resetCodes = new Map();
const smtpReady = !!(process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS && process.env.SMTP_FROM);
const mailer = smtpReady ? nodemailer.createTransport({host:process.env.SMTP_HOST,port:Number(process.env.SMTP_PORT||587),secure:String(process.env.SMTP_SECURE||'false')==='true',auth:{user:process.env.SMTP_USER,pass:process.env.SMTP_PASS}}) : null;
const resetAttempts = new Map();
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.resolve('.')));
let db = { users: {}, messages: [], contacts: {} };
try { if (fs.existsSync(DB)) db = { ...db, ...JSON.parse(fs.readFileSync(DB, 'utf8')) }; } catch (e) { console.error('Database file could not be read:', e.message); }
const save = () => { const tmp = DB + '.tmp'; fs.writeFileSync(tmp, JSON.stringify(db, null, 2)); fs.renameSync(tmp, DB); };
// Keep the configured owner account as admin after restarts, including existing accounts.
if (ADMIN_EMAIL && db.users[ADMIN_EMAIL]) {
  let changed = false;
  for (const u of Object.values(db.users)) {
    const role = u.email === ADMIN_EMAIL ? 'admin' : 'user';
    if (u.role !== role) { u.role = role; changed = true; }
  }
  if (changed) save();
}
const cleanEmail = e => String(e || '').trim().toLowerCase();
const validGmail = e => /^[^\s@]+@gmail\.com$/.test(e);
const publicUser = u => ({ uid: u.uid, email: u.email, name: u.name, role: u.role || 'user', blocked: !!u.blocked, createdAt: u.createdAt });
function tokenFor(u) { return jwt.sign({ uid: u.uid, email: u.email }, JWT_SECRET, { expiresIn: '30d' }); }
function auth(req, res, next) {
  try {
    const t = String(req.headers.authorization || '').replace(/^Bearer\s+/i, '');
    const claims = jwt.verify(t, JWT_SECRET); const u = db.users[claims.email];
    if (!u || u.uid !== claims.uid) return res.status(401).json({ error: 'Please log in again.' });
    if (u.blocked) return res.status(403).json({ error: 'This account has been blocked by the app owner.' });
    req.user = u; next();
  } catch { res.status(401).json({ error: 'Please log in again.' }); }
}
function adminOnly(req, res, next) { if (!req.user || req.user.role !== 'admin' || req.user.email !== ADMIN_EMAIL) return res.status(403).json({ error: 'Owner admin access required.' }); next(); }
app.get('/health', (_req, res) => res.json({ ok: true, service: 'DC Chat India', time: new Date().toISOString() }));
app.get('/register', (_req, res) => res.sendFile(path.resolve('register.html')));
app.get('/admin', (_req, res) => res.sendFile(path.resolve('admin.html')));

// Password recovery: OTPs are short-lived, one-use, rate-limited, and never returned by the API.
app.post('/api/forgot-password', async (req, res) => {
  const email = cleanEmail(req.body.email);
  if (!validGmail(email)) return res.status(400).json({error:'Enter the Gmail address used for your DC Chat account.'});
  if (!mailer) return res.status(503).json({error:'Password recovery email is not configured yet. Please try again later.'});
  const now = Date.now(), last = resetAttempts.get(email) || 0;
  if (now-last < 60_000) return res.status(429).json({error:'Please wait one minute before requesting another code.'});
  resetAttempts.set(email, now);
  const user = db.users[email];
  // Same generic response prevents exposing whether an account exists.
  if (!user) return res.json({ok:true,message:'If a DC Chat account exists for this Gmail, a verification code has been sent.'});
  const code = String(crypto.randomInt(0,1000000)).padStart(6,'0');
  resetCodes.set(email,{hash:crypto.createHash('sha256').update(code).digest('hex'),expires:now+10*60_000,attempts:0});
  try {
    await mailer.sendMail({
      from:process.env.SMTP_FROM,
      to:email,
      subject:'Your DC Chat password reset code',
      text:`Hello ${user.name},\n\nYour DC Chat password reset code is: ${code}\n\nIt expires in 10 minutes and can be used only once. If you did not request this, ignore this email. DC Chat support will never ask you to share this code.\n\nDC Chat Team`,
      html:`<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:24px;color:#17202a"><div style="background:#075e54;color:white;padding:18px;border-radius:12px;font-size:22px;font-weight:bold">DC Chat</div><h2>Password reset</h2><p>Hello ${String(user.name).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))},</p><p>Use this verification code to reset your password:</p><div style="font-size:32px;font-weight:bold;letter-spacing:8px;background:#f0f2f5;padding:18px;text-align:center;border-radius:10px">${code}</div><p>This code expires in 10 minutes and can be used once. If you did not request it, ignore this email.</p><small>DC Chat will never ask you to share this code.</small></div>`
    });
    return res.json({ok:true,message:'Verification code sent if this Gmail is registered. Check Inbox and Spam.'});
  } catch (err) {
    resetCodes.delete(email);
    console.error('Password reset email failed:',err?.message||'unknown error');
    return res.status(502).json({error:'Could not send the email right now. Please try again later.'});
  }
});
app.post('/api/reset-password', async (req,res) => {
  const email=cleanEmail(req.body.email), code=String(req.body.code||'').trim(), password=String(req.body.password||'');
  if(!validGmail(email)||!/^\d{6}$/.test(code)) return res.status(400).json({error:'Enter your Gmail and the 6-digit code.'});
  if(password.length<8) return res.status(400).json({error:'New password must be at least 8 characters.'});
  const entry=resetCodes.get(email), user=db.users[email];
  if(!entry||!user||entry.expires<Date.now()) { resetCodes.delete(email); return res.status(400).json({error:'Code expired or invalid. Request a new code.'}); }
  entry.attempts++;
  const candidate=crypto.createHash('sha256').update(code).digest('hex');
  if(entry.attempts>5||!crypto.timingSafeEqual(Buffer.from(entry.hash,'hex'),Buffer.from(candidate,'hex'))) {
    if(entry.attempts>=5) resetCodes.delete(email);
    return res.status(400).json({error:'Code is incorrect or expired. Check the email and try again.'});
  }
  user.password=await bcrypt.hash(password,12);
  save(); resetCodes.delete(email);
  return res.json({ok:true,message:'Password updated. You can now log in with your new password.'});
});

app.post('/api/register', async (req, res) => {
  const email = cleanEmail(req.body.email), password = String(req.body.password || ''), name = String(req.body.name || email.split('@')[0]).trim().slice(0, 40);
  if (!validGmail(email) || password.length < 8 || !name) return res.status(400).json({ error: 'Use a Gmail address, display name, and password of at least 8 characters.' });
  if (db.users[email]) return res.status(409).json({ error: 'This Gmail already has a DC Chat account. Please log in.' });
  const role = ADMIN_EMAIL && email === ADMIN_EMAIL ? 'admin' : 'user';
  const u = { uid: 'DC' + crypto.randomBytes(4).toString('hex').toUpperCase(), email, name, password: await bcrypt.hash(password, 12), role, blocked: false, createdAt: Date.now() };
  db.users[email] = u; save(); res.status(201).json({ token: tokenFor(u), user: publicUser(u) });
});
app.post('/api/login', async (req, res) => {
  const email = cleanEmail(req.body.email), password = String(req.body.password || ''), u = db.users[email];
  if (!validGmail(email) || !u || !(await bcrypt.compare(password, u.password))) return res.status(401).json({ error: 'Gmail address or password is incorrect, or account is not registered.' });
  if (u.blocked) return res.status(403).json({ error: 'This account has been blocked by the app owner.' });
  res.json({ token: tokenFor(u), user: publicUser(u) });
});
app.get('/api/me', auth, (req, res) => res.json(publicUser(req.user)));
app.get('/api/users/:uid', auth, (req, res) => { const uid = String(req.params.uid || '').toUpperCase(); const u = Object.values(db.users).find(x => x.uid === uid && !x.blocked); if (!u) return res.status(404).json({ error: 'DC UID not found.' }); res.json({ uid:u.uid, email:u.email, name:u.name }); });
// Server-saved contacts: each account can only read/write its own contact list.
app.get('/api/contacts', auth, (req, res) => {
  const ids = Array.isArray(db.contacts[req.user.uid]) ? db.contacts[req.user.uid] : [];
  const contacts = ids.map(uid => Object.values(db.users).find(u => u.uid === uid && !u.blocked))
    .filter(Boolean).map(u => ({ uid:u.uid, email:u.email, name:u.name }));
  res.json(contacts);
});
app.post('/api/contacts', auth, (req, res) => {
  const uid = String(req.body.uid || '').trim().toUpperCase();
  if (!uid || uid === req.user.uid) return res.status(400).json({error:'Enter another user’s DC UID.'});
  const target = Object.values(db.users).find(u => u.uid === uid && !u.blocked);
  if (!target) return res.status(404).json({error:'No active DC Chat account found for that UID.'});
  const ids = Array.isArray(db.contacts[req.user.uid]) ? db.contacts[req.user.uid] : [];
  if (!ids.includes(uid)) ids.push(uid);
  db.contacts[req.user.uid] = ids;
  save();
  res.status(201).json({uid:target.uid,email:target.email,name:target.name});
});
app.get('/api/messages/:peer', auth, (req, res) => { const me = req.user.uid, peer = String(req.params.peer); res.json(db.messages.filter(m => (m.from === me && m.to === peer) || (m.from === peer && m.to === me)).slice(-500)); });
app.post('/api/messages', auth, (req, res) => {
  const to = String(req.body.to || '').trim().toUpperCase(), text = String(req.body.text || '').slice(0, 10000);
  if (!to || !text.trim()) return res.status(400).json({ error: 'Recipient and message are required.' });
  const target = Object.values(db.users).find(x => x.uid === to && !x.blocked); if (!target) return res.status(404).json({ error: 'Recipient DC UID not found.' });
  const m = { id:crypto.randomUUID(), from:req.user.uid, to:target.uid, text, ts:Date.now(), time:Date.now(), read:false, status:'sent' };
  db.messages.push(m); save(); io.to('user:'+target.uid).emit('message:new', m); res.status(201).json(m);
});
// Owner-only admin API: never trusts a role sent by the browser.
app.get('/api/admin/users', auth, adminOnly, (_req, res) => res.json(Object.values(db.users).map(publicUser)));
app.post('/api/admin/users/:uid/block', auth, adminOnly, (req, res) => {
  const uid = String(req.params.uid || '').toUpperCase(); const u = Object.values(db.users).find(x => x.uid === uid);
  if (!u) return res.status(404).json({ error: 'User not found.' });
  if (u.email === ADMIN_EMAIL) return res.status(400).json({ error: 'Owner account cannot be blocked.' });
  u.blocked = req.body.blocked !== false; save();
  if (u.blocked) io.to('user:'+u.uid).emit('account:blocked');
  res.json(publicUser(u));
});
app.get('/api/admin/stats', auth, adminOnly, (_req, res) => res.json({ users:Object.keys(db.users).length, messages:db.messages.length, blocked:Object.values(db.users).filter(u=>u.blocked).length }));
io.use((socket, next) => { try { const claims = jwt.verify(socket.handshake.auth?.token, JWT_SECRET); const u = db.users[claims.email]; if (!u || u.uid !== claims.uid || u.blocked) return next(new Error('Unauthorized')); socket.user = u; next(); } catch { next(new Error('Unauthorized')); } });
io.on('connection', socket => {
  socket.join('user:'+socket.user.uid); socket.emit('ready', { uid:socket.user.uid });
  socket.on('typing', ({to, typing}={}) => { if (typeof to === 'string') io.to('user:'+to).emit('typing', {from:socket.user.uid, typing:!!typing}); });
  socket.on('read', ({id}={}) => { const m = db.messages.find(x => x.id === id && x.to === socket.user.uid); if (m) { m.read = true; save(); io.to('user:'+m.from).emit('message:read', {id:m.id}); } });
});
app.get('/', (_req, res) => res.sendFile(path.resolve('DC_Chat_Final_V20.html')));
server.listen(PORT, () => console.log(`DC Chat listening on port ${PORT}`));
