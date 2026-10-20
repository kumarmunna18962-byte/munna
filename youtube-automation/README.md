# Nandni YouTube Automation starter

A starter dashboard for original Hindi story planning and authorized YouTube integration.

## Render
Build command: `cd youtube-automation && npm install`
Start command: `cd youtube-automation && npm start`
Health endpoint: `/health`

## Google Cloud
Enable YouTube Data API v3 and create an OAuth Client ID of type Web application. Add the deployed callback URL exactly as an Authorized redirect URI: `https://YOUR-RENDER-SERVICE.onrender.com/oauth2callback`. Set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `GOOGLE_REDIRECT_URI` in Render Environment. Never commit secrets.

## Limitations
This is a starter only. It does not render cartoon videos, synthesize voice, persist OAuth tokens, schedule uploads, or upload videos yet. Token storage and upload functionality must be implemented and tested securely. Only publish original content or content with explicit permission/license; editing someone else's YouTube video does not automatically make it copyright-free.