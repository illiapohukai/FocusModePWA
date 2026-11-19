# Cloudflare Worker — Hardening Requirements (v17.17)

**Status: requirements only.** The Worker source (`worker.js`) is not in this repository and the deployed
endpoint was not reachable from the audit sandbox. Per `CLAUDE.md`, Worker code, endpoints and request/
response contracts are not changed from this repository. Historical notes in `CHANGELOG.md` (v17.4.2) claim
`MAX_ATTEMPTS = 2`, transient-only retries, per-attempt timeouts, a 4 MB image limit, `maxOutputTokens: 1536`
and structured errors with `requestId`; **none of this was verified**.

The owner should verify each item in the Worker repository/dashboard and record the result.

## 1. Contract (frozen, from the client code)

| Route | Method | Content type | Fields |
|---|---|---|---|
| `/create-mission` | POST | `multipart/form-data` | `goalText` (client caps at 6000 chars), `missionType` ∈ {exam, assignment, project, custom}, `confidence` ∈ {uncertain, partial, confident}, optional `schedule` JSON, optional `image` (JPEG/PNG, client-re-encoded, ≤ 1.5 MB, ≤ 1800 px, filename `material.jpg`/`material.png`) |
| `/adapt-mission` | POST | `application/json` | see `requestAdaptation()` in `optional-adaptation.js` |
| `/health` | GET | — | version/model info |

Responses: `{ ok: true, plan }` / `{ ok: true, adaptation }` or `{ ok: false, error: { code, message, retryable, requestId } }`.

## 2. Required controls

| # | Control | Requirement | Verified? |
|---|---|---|---|
| W1 | Methods | Only `POST` for the two AI routes, `GET` for `/health`, `OPTIONS` for preflight; everything else `405` | **[TO VERIFY]** |
| W2 | Routes | Unknown paths `404` without upstream call | **[TO VERIFY]** |
| W3 | CORS | Exact allowlist from `ALLOWED_ORIGIN` (comma-separated): production origin(s) + approved preview; localhost only in a dev environment. Echo the matched origin, `Vary: Origin`, `Access-Control-Allow-Methods: POST, OPTIONS`, `Access-Control-Allow-Headers: Content-Type`, `Access-Control-Max-Age: 600`, no `Allow-Credentials`. Never `*`. Disallowed origin → `403` before any upstream work. CORS is not authentication. | **[TO VERIFY]** |
| W4 | Content type | `/create-mission` requires `multipart/form-data`; `/adapt-mission` requires `application/json`; else `415` | **[TO VERIFY]** |
| W5 | Body size | Reject `Content-Length` > 5 MB (create) / 64 KB (adapt) before reading; enforce again while reading | **[TO VERIFY]** |
| W6 | Field validation | Types, enums, `goalText` ≤ 6000, schedule dates `YYYY-MM-DD` and ranges, weekdays 1–7, session minutes ∈ {20,30,45}, adapt payload ≤ 20 remaining steps, strings ≤ 2000 chars | **[TO VERIFY]** |
| W7 | Attachments | ≤ 1 image; MIME ∈ {image/jpeg, image/png, image/webp}; magic-byte check; ≤ 4 MB | **[TO VERIFY]** |
| W8 | Model and output | Model pinned server-side (never from the request); `maxOutputTokens` capped; response JSON schema validated (2–6 sessions, 5–60 min, non-empty strings, **step IDs `^[A-Za-z0-9_.:-]{2,160}$`**) before returning | **[TO VERIFY]** |
| W9 | Timeouts | Per-attempt `AbortController`; total budget below client timeouts (38 s text / 52 s image) | **[TO VERIFY]** |
| W10 | Retries | Max 1 retry, only for 408/429/5xx/timeout from upstream; never for 4xx validation; no recursion | **[TO VERIFY]** |
| W11 | Errors | Stable codes (`INVALID_REQUEST`, `UNSUPPORTED_FILE_TYPE`, `FILE_TOO_LARGE`, `AI_TIMEOUT`, `AI_RATE_LIMITED`, `AI_UNAVAILABLE`, `INVALID_AI_RESPONSE`, `INTERNAL_ERROR`); no raw upstream bodies, stack traces or key material; `requestId` (random, `^[A-Za-z0-9_-]{4,64}$`) in every response | **[TO VERIFY]** |
| W12 | Secrets | `GEMINI_API_KEY` only as a Worker **secret**, sent via `x-goog-api-key` header, never logged, never echoed | **[TO VERIFY]** |
| W13 | Logging | Log only: requestId, timestamp, route, status class, upstream status class, latency, input byte size, attachment count, token usage, rate-limit decision, app version. Never: goal text, feedback, image bytes, full prompt, full model response, headers with credentials, IP beyond what Cloudflare stores by default. Debug logging off by default. | **[TO VERIFY]** |
| W14 | Response headers | `Content-Type: application/json; charset=utf-8`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cross-Origin-Resource-Policy: same-site` (or omit if it breaks CORS), no `Set-Cookie` | **[TO VERIFY]** |
| W15 | Duplicate protection | Optional: accept an idempotency key in a **future, coordinated** contract version (KV/Durable Object, 2-minute window, return stored response on replay). Not required by the current client, which already prevents duplicate submissions (single owner + request token). | Deferred |

## 3. Rate limiting and cost control (dashboard / `wrangler.toml`)

Without accounts or attestation a public endpoint cannot distinguish the PWA from a custom client
(Origin/Referer/User-Agent are spoofable outside browsers). Controls therefore limit **damage**, not identity.

1. **Workers Rate Limiting binding** (code-based, reviewable) — e.g. in `wrangler.toml`:
   ```toml
   [[unsafe.bindings]]
   name = "AI_BURST_LIMIT"
   type = "ratelimit"
   namespace_id = "1001"
   simple = { limit = 5, period = 60 }   # per key per minute
   ```
   Key: `cf-connecting-ip` (+ route). Return `429 AI_RATE_LIMITED` with `retryable: true` and `Retry-After`.
   Verify the current binding syntax in Cloudflare's documentation before use **[TO VERIFY]**.
2. **Daily abuse limit**: KV/Durable Object counter per IP (e.g. 40 create + 40 adapt per 24 h) and a global
   daily ceiling that switches the Worker to `503 AI_UNAVAILABLE` when exceeded.
3. **WAF rate-limiting rule** (dashboard, if the plan allows): path `/create-mission|/adapt-mission`,
   method POST, e.g. 10 requests / 1 minute per IP → block 10 minutes.
4. **Google Cloud budget alerts** on the Gemini API project (50 %/90 %/100 %) and an API-key restriction to the
   Generative Language API only. Consider a hard quota cap in Google Cloud.
5. **Cloudflare usage notifications** for Workers requests/CPU.
6. CAPTCHA/Turnstile: not needed now; consider only if abuse is observed.

## 4. Acceptance checks (run against a staging Worker, low volume)

```bash
W=https://<staging-worker>.workers.dev
# Disallowed origin is rejected before upstream work
curl -si -X OPTIONS "$W/create-mission" -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST' | head -20
# Allowed origin preflight
curl -si -X OPTIONS "$W/create-mission" -H 'Origin: https://<production-origin>' -H 'Access-Control-Request-Method: POST' | grep -i 'access-control\|vary'
# Wrong method / content type / oversized / invalid schema
curl -si -X GET  "$W/create-mission" | head -1                                         # expect 405
curl -si -X POST "$W/create-mission" -H 'Content-Type: text/plain' --data 'x' | head -1 # expect 415
head -c 6000000 /dev/zero | curl -si -X POST "$W/create-mission" -F 'goalText=x' -F 'image=@-;type=image/png' | head -1 # expect 413
curl -si -X POST "$W/create-mission" -F 'goalText=' -F 'missionType=evil' | head -1    # expect 400, no upstream call
# Error bodies contain code + requestId, no stack trace, no key
curl -s -X POST "$W/adapt-mission" -H 'Content-Type: application/json' --data '{}' | grep -vi 'AIza\|stack'
# Rate limit: 6 quick requests from one IP (staging only)
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -w '%{http_code}\n' -X POST "$W/create-mission" -F 'goalText=Test' -F 'missionType=custom' -F 'confidence=partial'; done
```

Record results in the security baseline before public beta.
