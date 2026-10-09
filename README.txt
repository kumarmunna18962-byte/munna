DC CHAT INDIA — ONLINE STARTER V21 (OWNER ADMIN)

INCLUDED
- DC_Chat_Final_V20.html: existing V20 user interface (preserved).
- register.html: separate account registration page.
- server.js: Express + Socket.IO API, password hashing, DC UID, one-to-one text messaging, typing/read events, owner-only admin API.
- admin.html: owner dashboard to view accounts, see basic counts, block/unblock users.
- package.json: Node.js dependencies.

IMPORTANT
This ZIP is source code, not a live website. Real accounts/messages only work after hosting the Node server and setting environment variables. The starter still stores data in a JSON file; use a managed database or correctly mounted persistent disk before public use.

DEPLOYMENT ENVIRONMENT VARIABLES
1. NODE_ENV=production
2. DC_CHAT_SECRET=a unique random secret with at least 32 characters (do not share or commit it).
3. DC_CHAT_ADMIN_EMAIL=the owner's Gmail address (lowercase, @gmail.com). The owner must register this exact Gmail at /register after these variables are configured; only that account gets the admin role.
4. Optional: DC_CHAT_DB=/path/on/persistent/disk/dc-chat-data.json

AFTER HOSTING
- Open /health and check that it returns {"ok":true,...}.
- Open /register and create the owner's account first using a unique DC Chat password (not the Gmail password).
- Open /admin and sign in with that owner account to manage users.
- Other people can register at /register and then use the main app URL. Share DC UIDs to find each other.

SECURITY / LIMITS
- Gmail syntax is checked, but inbox ownership is NOT verified by email confirmation. Do not call this Gmail-verified authentication.
- Admin permissions are enforced by the server, not by hiding buttons in the browser.
- JSON-file storage is only for a small prototype; simultaneous writes, backups, and scale need a proper database. Do not launch publicly for a large audience until database persistence, rate limiting, email verification, monitoring, and security review are added.
- Voice/video calls, push notifications and media storage are not enabled by this backend.
