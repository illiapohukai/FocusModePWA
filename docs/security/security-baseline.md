# Focus Mode+ — Security & Privacy Baseline (v17.17)

A proportionate baseline for a local-first demo PWA. It is **not** a certification, penetration test or legal
opinion. Status values: **Implemented**, **Partially implemented**, **Documented**, **Deferred**,
**Not applicable**.

## 1. Implemented and tested measures

| Area | Measure | Status | Tested by |
|---|---|---|---|
| Import | Byte limit (5 MB) before reading; empty/malformed/non-object rejection | Implemented | `data-security.test.js`, browser suite |
| Import | Structural scan: depth ≤ 20, ≤ 250 000 values, ≤ 200 keys/object, ≤ 10 000 items/array, strings ≤ 50 000, keys ≤ 100 chars (iterative, no recursion) | Implemented | `data-security.test.js` |
| Import | Rejects `__proto__`, `prototype`, `constructor` anywhere; no recursive merge of untrusted objects | Implemented | `data-security.test.js`, browser suite |
| Import | Rejects reserved server-authoritative field names (`verified*`, `leagueScore`, `subscription*`, …) | Implemented | `data-security.test.js` |
| Import | Explicit format/scope check; unknown formats and future schemas rejected; older Focus Mode+ exports (v16–v17.16, legacy root arrays) accepted | Implemented | fixtures from pre-change code |
| Import | Domain limits (§3) and field validation (IDs, enums, durations, timestamps, `YYYY-MM-DD` dates, lists) in the single validator `src/data/data-validator.js` | Implemented | `data-security.test.js` |
| Import | Mission/step field whitelist; unknown Mission fields dropped | Implemented | `data-security.test.js` |
| Import | SHA-256 verification when a digest is present; mismatch → "Datei beschädigt" | Implemented | both suites |
| Import | Preview: format, export date, app version, counts, date range, settings, overlapping Missions, current data that will be replaced, integrity status, local-backup disclaimer | Implemented | browser suite |
| Import | Newer file selection supersedes an older pending preview | Implemented | `data-security.test.js` |
| Import | Required pre-import backup (abort if it cannot be stored) | Implemented | `data-security.test.js` |
| Import / restore | Transactional apply: every write checked + read back; rollback to the in-memory snapshot on failure; quota errors classified | Implemented | `data-security.test.js` (simulated write and quota failure) |
| Export | Envelope `format: "focusmode-backup"`, `exportVersion: 1` (unchanged, backward/forward compatible), `appVersion`, `scope`, `trust: "local_backup_unverified"`, SHA-256 digest over canonical JSON | Implemented | both suites; forward-compat check with v17.16 code |
| Export | "Nur Missionen & Einstellungen" (no Session History); privacy note before export; filenames without user content | Implemented | both suites |
| Export | Internal migration metadata and raw snapshot excluded; unverified FNV checksum removed | Implemented | `data-security.test.js` |
| Rendering | All dynamic HTML paths escape text; IDs that reach inline-handler strings must pass the safe-ID policy at AI, adaptation, repository and import boundaries (unsafe/duplicate IDs regenerated, references remapped) | Implemented | `ai-boundary.test.js`, browser suite (hostile backup + AI payload across screens) |
| AI output | Length/list caps, control-character removal, bounded numbers | Implemented | `ai-boundary.test.js` |
| AI client | Single active request owner + per-request token (cancel/retry race fixed); goal text ≤ 6000 chars; `credentials: 'omit'`, `cache: 'no-store'` | Implemented | browser suite (mocked Worker) |
| AI client | Errors mapped to German messages; internal exception text never shown; server text capped; request ID shown as support reference when safe; logs contain codes only | Implemented | `ai-boundary.test.js`, browser suite |
| Attachments | 1 file; MIME allowlist; magic-byte check must match; header dimensions ≤ 12 000 px/side and ≤ 40 MP before decode, re-checked after decode; ≤ 20 MB input, ≤ 1.5 MB output; always re-encoded (EXIF/GPS removed); generic upload filename; object URLs revoked | Implemented | `ai-boundary.test.js`, browser suite |
| Privacy | AI transparency note on the goal screen (non-blocking `<details>`) | Implemented | browser suite |
| Privacy | "Alle lokalen Daten löschen" (incl. backups) with explicit confirmation | Implemented | both suites |
| Privacy | Google Fonts removed (self-hosted Inter); unpkg removed (vendored Lucide) | Implemented | browser suite (no third-party requests except Tailwind) |
| Supply chain | Lucide pinned to 1.76.0, vendored with licence and SHA-256 (§6) | Implemented | browser suite |
| CSP | `<meta>` CSP (§4) + `Referrer-Policy` meta | Partially implemented | browser suite: zero violations with the Tailwind 3.4 bundle |
| Service worker | Worker host/API paths bypassed; non-GET bypassed; same-origin runtime caching only for static asset types; docs/tests/.claude/Markdown never cached; only the Tailwind host cached cross-origin; failed/HTML-fallback responses not cached; app shell only refreshed from the shell URL; cache bumped to `focus-mode-v17-17-security-privacy` | Implemented | `sw-policy.test.js`, browser suite (cache contents, offline reload) |
| Storage | Gateway classifies quota errors, lists/removes only app keys, never logs values | Implemented | `data-security.test.js` |
| Trust boundary | Trust classes and `isServerAuthoritative()` (always false locally) in the schema registry | Implemented | `data-security.test.js` |
| Secrets | Scan of tracked files and full Git history; `.gitignore` for env/credential files | Implemented | manual scan (no findings) |

## 2. Unknown-field policy

- Backup root: known fields are read explicitly; unknown root fields are **ignored** (never copied).
- Missions and steps: **whitelisted** to the fields the repository persists; unknown fields are dropped.
- Session records: unknown fields are **preserved within the structural limits** for forward compatibility
  (Session objects are produced by several modules and newer versions may add fields). They are never used as
  configuration, handlers or URLs and pass the dangerous/reserved-key scan.
- Preferences: only `language` and `pwaInstallHelpDismissed` are applied.

## 3. Import limits

| Limit | Value | Rationale |
|---|---|---|
| File size | 5 MB | A full real export (4 Missions, 12 Sessions) is ~27 KB; 500 Sessions ≈ 0.6 MB |
| Depth | 20 | Real exports have depth 8 |
| Values (nodes) | 250 000 | ≫ legitimate maximum (~30 000) |
| Keys per object | 200 | Largest real object has 17 keys |
| Array length | 10 000 | Generic guard |
| Missions | 250 | Real use: a handful (max. 2 active) |
| Steps per Mission | 100 | AI produces ≤ 6; adaptation ≤ 20 |
| Sessions | 5 000 accepted; newest 500 kept | App keeps 500 |
| Progress history per Mission | 1 000 | |
| List items (tools/apps) | 100 | |
| Title | 300 chars | |
| Subject / deadline text | 300 chars | |
| Task / reason / feedback / explanation | 20 000 chars | |
| Any string | 50 000 chars | |
| IDs | 2–160 chars `[A-Za-z0-9_.:-]` | existing policy |
| Durations | 0–1440 min; elapsed ≤ 48 h | |
| Timestamps | epoch ms or ISO string between 1970 and 2100 | |

## 4. Content-Security-Policy (meta, `index.html`)

```
default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com;
style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self';
connect-src 'self' https://focusmodedemoai.illia-pohukai.workers.dev; media-src 'self';
worker-src 'self'; manifest-src 'self'; frame-src 'none'; object-src 'none';
base-uri 'self'; form-action 'self'
```

Exceptions and why:

- `script-src 'unsafe-inline'`: ~158 inline `on*=` handlers in `index.html` plus generated handler strings and
  two inline bootstrap scripts. Nonces are impossible on a static host; hashes would need `'unsafe-hashes'`
  for every handler. Migration to event delegation is required for a strict policy.
- `https://cdn.tailwindcss.com`: Tailwind Play CDN (see §6). Its 3.4 bundle was inspected: no `eval`/
  `Function`, so **no `'unsafe-eval'`**.
- `style-src 'unsafe-inline'`: Tailwind injects a `<style>` element; many inline `style=` attributes.
- `img-src data: blob:`: CSS data URIs and the selected-photo preview (`blob:`).
- Not settable via `<meta>`: `frame-ancestors`, `report-uri`/`report-to`, `sandbox`. GitHub Pages cannot send
  custom headers, so `X-Content-Type-Options`, `Permissions-Policy`, `Cross-Origin-Opener-Policy` and a header
  CSP are **not delivered**. Production headers were **not** verified on the deployed origin.

Recommended header set once a header-capable layer exists (e.g. Cloudflare in front of a custom domain):
the CSP above as a header plus `frame-ancestors 'none'`; `X-Content-Type-Options: nosniff`;
`Referrer-Policy: strict-origin-when-cross-origin`; `Permissions-Policy: camera=(self), microphone=(), geolocation=(), payment=(), usb=()`;
`Cross-Origin-Opener-Policy: same-origin`; `Strict-Transport-Security: max-age=31536000; includeSubDomains`
(custom domain only; `github.io` is already HSTS-preloaded). Do not add COEP (would break the CDN script).

## 5. Logging and redaction policy (one policy for client and Worker)

- Client: console logs contain event names, codes, counts, key names and request IDs — never Mission text,
  goal text, feedback, image data or full server messages.
- Worker: see `worker-hardening-requirements.md` W13. Debug logging off by default.

## 6. Dependency and supply-chain inventory

| Dependency | Purpose | Version | Source | Integrity | Update mechanism | Necessary? |
|---|---|---|---|---|---|---|
| Tailwind CSS Play CDN | Utility classes at runtime | latest 3.x served by CDN (unpinned) | `https://cdn.tailwindcss.com` | none possible (no CORS → no SRI) | automatic upstream | Replace with pre-built CSS (F-06) |
| Lucide | Icons | 1.76.0 (= `@latest` at audit time) | npm `lucide@1.76.0` `dist/umd/lucide.min.js` → `assets/vendor/lucide/` | SHA-256 `7de3b98fb62b89fac714ee9303d24253d90a0991cbb6aa06516d87c29dca42bb`, ISC licence included | manual: download from npm, verify, update hash here, bump SW cache | Yes |
| Inter (variable) | Font | @fontsource-variable/inter 5.3.0 (latin, latin-ext, cyrillic) | npm → `assets/fonts/inter/` | SHA-256: latin `3100e775…4c62`, latin-ext `34b9c504…c956`, cyrillic `71d5ee93…0075`; OFL licence included | manual | Yes |
| npm packages | — | none (no `package.json`) | — | — | — | — |
| GitHub Actions | — | none (`.github/` absent) | — | — | — | — |
| Cloudflare config | Worker | not in repo | dashboard | — | manual deploy | — |
| `.claude/skills/ui-ux-pro-max` | Development skill | — | repo | — | — | Not runtime; never precached or runtime-cached |

## 7. Known limitations (accepted for the demo stage)

1. Local data (Missions, History, streak, achievements) is fully user-editable and **not** suitable for any
   competitive or paid purpose. No local mechanism can change that.
2. Backup integrity detects corruption only; anyone can recompute the digest.
3. `localStorage` content is plaintext; no client-side encryption with shipped keys (it would be theatre).
4. `script-src 'unsafe-inline'` remains; CSP mainly limits loading and exfiltration targets.
5. Tailwind Play CDN executes third-party code at runtime.
6. Worker abuse controls are unverified; a public endpoint cannot distinguish the PWA from a custom client.
7. No header-based protections (clickjacking protection, `nosniff`) on GitHub Pages.
8. Legacy modules still access `localStorage` directly (F-17).
9. On `<account>.github.io/<repo>` all Pages projects of the account share one origin and therefore one
   `localStorage` (F-26). A dedicated origin is required before public beta.

## 8. Deployment steps requiring manual action

- Verify and record Worker controls W1–W14 and configure rate limiting + budget alerts
  (`worker-hardening-requirements.md`).
- Confirm `ALLOWED_ORIGIN` contains only the production origin (and approved previews).
- Fill in `SECURITY.md` contact, privacy notice placeholders and processor register.
- Optional: put the site behind a header-capable layer and deploy the header set from §4.

## 9. Checklists

### Before public beta (none marked complete without evidence)
- [ ] Privacy notice reviewed by a qualified person and published
- [ ] Impressum completed where legally required
- [ ] Processor agreements checked (GitHub, Cloudflare, Google)
- [ ] Gemini data-use/retention terms for the used tier confirmed
- [ ] Cloudflare terms and logging configuration confirmed
- [ ] Production origin allowlist set (`ALLOWED_ORIGIN`)
- [ ] Rate limiting enabled and tested on staging
- [ ] Budget alerts / quota caps enabled
- [ ] Security contact added to `SECURITY.md`
- [ ] Production logging reviewed (no user content)
- [ ] Real CSP and security headers delivered (header-capable hosting)
- [ ] Dedicated origin (custom domain/subdomain) instead of a shared `github.io` origin
- [ ] Tailwind Play CDN replaced by a pre-built stylesheet
- [ ] Backup export → import → restore tested on target browsers (iOS Safari, Android Chrome)
- [ ] Account architecture decided
- [ ] Incident process defined (who, how to rotate the Gemini key, how to notify users)

### Before accounts
- [ ] Passkey/WebAuthn design reviewed; token storage outside `localStorage`
- [ ] Object-level authorisation on every API route
- [ ] Account export and deletion implemented
- [ ] Privacy notice and register updated

### Before Leagues (competitive features)
- [ ] Authenticated accounts
- [ ] Server-authoritative Session ledger with server timestamps
- [ ] Verified Session ownership
- [ ] Idempotent completion and duplicate protection
- [ ] Server-derived streak and score
- [ ] Import excluded from verified scoring (local backups never accepted as evidence)
- [ ] System-time manipulation mitigation (server clock, time-zone change limits)
- [ ] Abuse monitoring and anomaly flags
- [ ] Moderation/dispute process where relevant
- [ ] Privacy notice, deletion and export updated
- Current local streak must never become League score.

### Before payments
- [ ] Server-authoritative entitlements
- [ ] Webhook signature verification and idempotent event processing
- [ ] Entitlement reconciliation, refund and cancellation handling
- [ ] Invoice/tax handling, terms and cancellation information
- [ ] Account recovery and support contact
- [ ] Audit log
- [ ] Privacy documentation updated
