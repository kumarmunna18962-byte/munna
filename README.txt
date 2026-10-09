DC CHAT INDIA — ONLINE STARTER V20

WHAT THIS INCLUDES
- V20 frontend with Gmail + password login only (no Create Account button in the main app).
- Separate /register page for first-time account setup.
- Node.js + Express + Socket.IO backend, password hashing, DC UID, 1-to-1 text messages, typing/read events.
- /health endpoint for checking server status.

IMPORTANT
This ZIP is not itself a public server. It must be deployed to a Node.js host before separate phones can message each other. I cannot publish it to the internet without the owner's hosting account/authorization.

DEPLOY (Node.js host supporting persistent disk)
1. Upload/extract all files to a Node.js service.
2. Set Start Command: npm start
3. Set environment variable DC_CHAT_SECRET to a long random secret (32+ characters).
4. Ensure the service has a persistent disk mounted for dc-chat-data.json, or configure DC_CHAT_DB to a writable persistent path.
5. After deployment, open https://YOUR-SERVICE/register once to create the first account. This page is separate from the main login screen.
6. Open https://YOUR-SERVICE/ and log in with that Gmail/password. Friend must also create an account and share their DC UID.
7. Test https://YOUR-SERVICE/health; it should return {"ok":true,...}.

LIMITATIONS
- Gmail format is checked, but this starter does not verify ownership of the Gmail inbox. Do not reuse your Gmail password; choose a unique DC Chat password.
- JSON-file storage is for a small prototype only, not a production-grade database. Use a managed database and proper backups before public launch.
- Voice/video calling, push notifications and media storage need additional services and are not fully enabled by this backend.
