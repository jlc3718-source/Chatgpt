# Jason Home 2 — Eufy Mega SDK Shadow Lab

**STATUS: Prepared test integration; NOT installed in production.**

This standalone read-only service pins `@mega-yfue/eufy-sdk` to **0.5.0 stable** and requires Node.js **24.5+**. It runs on `127.0.0.1:48721`, uses a separate SDK client identity, and performs no device control writes. **Existing Jason Home 2 Wi-Fi, BLE, schedules, account sessions and light effects remain the production source of truth.**

## Safety gates

1. By default, SDK login is disabled. The service starts without credentials and only reports health.
2. Configure `EUFY_EMAIL`, `EUFY_PASSWORD`, `EUFY_COUNTRY_CODE=US`, a **new distinct** `JH2_MEGA_SHADOW_OPENUDID` (different from existing production client identity), and a random `JH2_MEGA_SHADOW_TOKEN` of at least 24 characters. Use a protected environment file, never source control or logs.
3. Only when explicitly approved for testing, set `JH2_MEGA_ALLOW_LOGIN=1` and manually call `POST /v1/login` with the Bearer token. It rate-limits new logins to once every five minutes. CAPTCHA/2FA are exposed as separate user-answered steps.
4. Read-only `GET /v1/devices` reports smart-light names, generic capabilities and reported power, brightness, and effect IDs. It excludes serial numbers and authentication tokens.
5. Stop/remove the sidecar or unset the flag to return to current production operation. SDK code is never imported by the Jason Home 2 production app.

## Install on isolated Node 24 host (not performed here)

```
npm install --omit=dev
node shadow.mjs
```

Default health: `GET http://127.0.0.1:48721/health`.

Other routes are Bearer-protected: `GET /v1/status`, `POST /v1/login`, `POST /v1/captcha`, `POST /v1/2fa`, and `GET /v1/devices`.

The SDK's current documentation verifies `setEffect()` for E22 but not E120; this lab calls none of those methods. Even with a successful SDK test, **do not move real light-control traffic** until E120/E22 behavior, sessions and scheduled effects are validated independently.

## Rollback

Stable label: `JH2-STABLE-2026-11-09`. Restore manifest and exact 42-source-code snapshot reside in the user's private Google Drive folder **Jason Home 2 — STABLE BASELINE — 2026-11-09**. This code lab is isolated from production and can be discarded with no change to production controls.

This baseline covers the 42 Oracle-development-authorized source files, **not** the host's OS image, databases, credentials, original separate Eufy upstream service or Nano firmware.