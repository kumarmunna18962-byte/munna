import express from 'express';
import http from 'node:http';
import { Server } from 'socket.io';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: true, methods: ['GET','POST'] } });
const PORT = process.env.PORT || 3000;
const SECRET = process.env.DC_CHAT_SECRET;
if (process.env.NODE_ENV === 'production' && (!SECRET || SECRET.length < 32)) {
  console.error('Set DC_CHAT_SECRET to a random secret of at least 32 characters.');
  process.exit(1);
}
const JWT_SECRET = SECRET || 'local-development-only-change-before-deploy-123456';
const DB = process.env.DC_CHAT_DB || path.resolve('dc-chat-data.json');
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.resolve('.')));
let db = { users: {}, messages: [] };
try { if (fs.existsSync(DB)) db = { ...db, ...JSON.parse(fs.readFileSync(DB, 'utf8')) }; }
catch (e) { console.error('Database file could not be read; starting with an empty database.', e.message); }
const save = () => { const tmp = DB + '.tmp'; fs.writeFileSync(tmp, JSON.stringify(db, null, 2)); fs.renameSync(tmp, DB); };
const cleanEmail = e => String(e || '').trim().toLowerCase();
const validGmail = e => /^[^\s@]+@gmail\.com$/.test(e);
function tokenFor(u) { return jwt.sign({ uid: u.uid, email: u.email }, JWT_SECRET, { expiresIn: '30d' }); }
function auth(req, res, next) { try { const t = String(req.headers.authorization || '').replace(/^Bearer\s+/i, ''); req.user = jwt.verify(t, JWT_SECRET); next(); } catch { res.status(401).json({ error: 'Please log in again.' }); } }
app.get('/health', (_req, res) => res.json({ ok: true, service: 'DC Chat India', time: new Date().toISOString() }));
// Separate registration page; the main app login screen remains Gmail + password only.
app.get('/register', (_req, res) => res.sendFile(path.resolve('register.html')));
app.post('/api/register', async (req, res) => {
  const email = cleanEmail(req.body.email), password = String(req.body.password || ''), name = String(req.body.name || email.split('@')[0]).trim().slice(0, 40);
  if (!validGmail(email) || password.length < 8 || !name) return res.status(400).json({ error: 'Use a valid Gmail, display name, and password of at least 8 characters.' });
  if (db.users[email]) return res.status(409).json({ error: 'This Gmail already has a DC Chat account. Please log in.' });
  const uid = 'DC' + crypto.randomBytes(4).toString('hex').toUpperCase();
  db.users[email] = { uid, email, name, password: await bcrypt.hash(password, 12), createdAt: Date.now() };
  save(); const u = db.users[email]; res.status(201).json({ token: tokenFor(u), user: { uid: u.uid, email: u.email, name: u.name } });
});
app.post('/api/login', async (req, res) => {
  const email = cleanEmail(req.body.email), password = String(req.body.password || ''), u = db.users[email];
  if (!validGmail(email) || !u || !(await bcrypt.compare(password, u.password))) return res.status(401).json({ error: 'Gmail address or password is incorrect, or account is not registered.' });
  res.json({ token: tokenFor(u), user: { uid: u.uid, email: u.email, name: u.name } });
});
app.get('/api/me', auth, (req, res) => { const u = db.users[req.user.email]; if (!u) return res.status(401).json({error:'Account not found'}); res.json({ uid:u.uid, email:u.email, name:u.name }); });
app.get('/api/users/:uid', auth, (req, res) => { const uid = String(req.params.uid || '').toUpperCase(); const u = Object.values(db.users).find(x => x.uid === uid); if (!u) return res.status(404).json({ error: 'DC UID not found' }); res.json({ uid:u.uid, email:u.email, name:u.name }); });
app.get('/api/messages/:peer', auth, (req, res) => { const me = req.user.uid, peer = String(req.params.peer); res.json(db.messages.filter(m => (m.from === me && m.to === peer) || (m.from === peer && m.to === me)).slice(-500)); });
app.post('/api/messages', auth, (req, res) => { const to = String(req.body.to || ''), text = String(req.body.text || '').slice(0, 10000); if (!to || !text.trim()) return res.status(400).json({error:'Recipient and message are required'}); const target = Object.values(db.users).find(x => x.uid === to); if (!target) return res.status(404).json({error:'Recipient DC UID not found'}); const m = { id:crypto.randomUUID(), from:req.user.uid, to:target.uid, text, ts:Date.now(), time:Date.now(), read:false, status:'sent' }; db.messages.push(m); save(); io.to('user:'+target.uid).emit('message:new', m); res.status(201).json(m); });
io.use((socket, next) => { try { socket.user = jwt.verify(socket.handshake.auth?.token, JWT_SECRET); next(); } catch { next(new Error('Unauthorized')); } });
io.on('connection', socket => { socket.join('user:'+socket.user.uid); socket.emit('ready', {uid:socket.user.uid}); socket.on('typing', ({to, typing}) => { if (typeof to === 'string') io.to('user:'+to).emit('typing', {from:socket.user.uid, typing:!!typing}); }); socket.on('read', ({id}) => { const m = db.messages.find(x => x.id === id && x.to === socket.user.uid); if (m) { m.read = true; save(); io.to('user:'+m.from).emit('message:read', {id:m.id}); } }); });
app.get('/', (_req, res) => res.sendFile(path.resolve('DC_Chat_Final_V20.html')));
server.listen(PORT, () => console.log(`DC Chat listening on port ${PORT}`));
