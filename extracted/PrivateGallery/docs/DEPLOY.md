# Deploying the signaling server

The signaling server is a Kotlin/Ktor JVM app — a different stack from AI Studio's own
Node.js-based "web app" pipeline, so deploy it directly to Cloud Run as a container rather than
importing it into AI Studio's web-app builder. AI Studio's Android app builder only needs the
resulting HTTPS/WSS URL (as `API_BASE_URL` / `SIGNALING_WS_URL`), not the server's source.

## 1. Postgres
Use any reachable Postgres instance — a free tier on Neon (neon.tech) or Supabase both work,
since the app only needs a standard `postgresql://` connection string. Create an empty database
and note its connection string; you'll set it as `DATABASE_URL` below. Table creation: add a
`SchemaUtils.createMissingTablesAndColumns(...)` call at startup in `Application.kt` for first
deploy (Exposed provides this), or run migrations manually from `model/Tables.kt`.

## 2. Secrets
Two secrets the server needs are generated below — treat them like passwords, never commit them:

```
JWT_SECRET=yI6EuhmDLJOVv2g092WPPJnStj/dXXrFp+hO/2sNQCo=
EMAIL_HMAC_PEPPER=sk041TbmPp/XqXRpIHiYbVtebovt1EwbuOukmwSLJh8=
```
(Generated with `openssl rand -base64 32` — regenerate your own if you'd rather not reuse the
ones printed in this document, and store them in Cloud Run's Secret Manager integration or as
deploy-time environment variables, never in source control.)

## 3. Build and deploy the container
From `signaling-server/`:
```bash
gcloud auth login
gcloud config set project <your-gcp-project-id>
gcloud run deploy private-gallery-signaling \
  --source . \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars JWT_SECRET="<paste>",EMAIL_HMAC_PEPPER="<paste>",DATABASE_URL="<paste>",DATABASE_USER="<paste>",DATABASE_PASSWORD="<paste>"
```
`gcloud run deploy --source .` builds the `Dockerfile` in this directory automatically (Cloud
Build) and gives you an HTTPS URL. WebSocket support (`/ws`) works on Cloud Run without extra
configuration, but Cloud Run's default request timeout (max 60 min) means a socket that's idle
that long gets recycled — the client already reconnects on `SignalingWebSocketClient` failure, so
this is a non-issue, not a bug to fix.

Take the resulting URL, e.g. `https://private-gallery-signaling-abc123-uc.a.run.app`, and derive:
- `API_BASE_URL = https://private-gallery-signaling-abc123-uc.a.run.app/`
- `SIGNALING_WS_URL = wss://private-gallery-signaling-abc123-uc.a.run.app/ws`

## 4. TURN
Pick one:
- **Managed**: a TURN-as-a-service provider (e.g. Twilio's Network Traversal Service, Cloudflare
  Calls TURN, or metered.ca) — sign up, and their dashboard gives you a URL + short-lived
  credential-minting API. Wire that minting call into a small `/turn-credentials` endpoint on this
  same signaling server (add a route that calls the provider's API server-side and returns
  `{urls, username, credential}` to the app) rather than hardcoding a static secret.
- **Self-hosted**: run `coturn` on a small VM with a static IP, using its `use-auth-secret` +
  time-limited HMAC credentials mode — cheaper at scale, more setup.

## 5. Firebase (push wake-up)
Create a Firebase project (console.firebase.google.com), add an Android app with package name
`com.privategallery.app`, download `google-services.json` into `app/`, and generate a service
account key for the server (Project Settings → Service Accounts) so the signaling server can call
the Firebase Admin SDK to send a data-only wake message when `sync-event` fires for an offline
recipient.
