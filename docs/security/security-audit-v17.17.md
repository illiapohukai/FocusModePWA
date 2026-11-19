# Focus Mode+ — Security & Privacy Audit v17.17

Status: internal working document. Written before implementation from a source review of branch
`claude/focus-mode-security-privacy-v17-f0sxle` (base commit `472f18c`, v17.16). The "v17.17" column was
updated after implementation and testing.

This is a proportionate source-code review by the development agent. It is **not** a penetration test, a
certification (OWASP, BSI, GDPR) or legal advice. The Cloudflare Worker source is **not** in this repository
and the Worker endpoint was **not reachable** from the audit sandbox (proxy policy), so every Worker
statement below is a requirement or a historical claim, not a verified fact.

## 1. Architecture summary

| Layer | Implementation | Owner files |
|---|---|---|
| Hosting | Static files on GitHub Pages (subpath, HTTPS). No server-side code of our own. | `index.html`, `manifest.webmanifest` |
| App runtime | Vanilla HTML/CSS/JS, classic `<script>` tags sharing `window.FocusModeApp`. ~158 inline `on*=` handlers. | `index.html`, `src/**` |
| Styling / icons / fonts | Tailwind **Play CDN** (`cdn.tailwindcss.com`), Lucide (was `unpkg.com/lucide@latest`), Inter (was Google Fonts). | `index.html` `<head>` |
| Local persistence | `localStorage` only (no IndexedDB, no sessionStorage, no cookies). | `src/services/storage-service.js`, `src/repositories/*`, `src/data/*` |
| Import / export / reset | JSON backup file, local automatic backups in `localStorage`. | `src/data/data-{export,import,reset,validator}.js`, `migration-runner.js`, `features/data-management/` |
| AI | `POST` from browser to Cloudflare Worker `focusmodedemoai.illia-pohukai.workers.dev` (`/create-mission` multipart, `/adapt-mission` JSON); Worker calls Google Gemini with a server-side secret. | `src/services/ai-service.js`, `src/features/optional-adaptation/optional-adaptation.js` |
| Offline | Service worker with versioned precache (`CORE`) and runtime caching. | `service-worker.js` |
| Accounts / payments / leagues | **None.** League screen is a static, labelled demo (`league.js`, `isDemo: true`). Streak and achievements are derived locally from Session History. | `src/features/league/league.js`, `src/features/history/focus-insights.js` |

## 2. Data inventory (summary)

Full inventory: [`docs/privacy/data-inventory.md`](../privacy/data-inventory.md).

- Local by default: Missions (title, subject, deadline text, steps, tasks, reasons, tools, AI source analysis),
  Mission progress history (outcome, rating, free-text feedback), Session History (≤ 500 records), active/last
  session, last AI plan, language, PWA-help flag, onboarding flag, schema/migration state, up to three
  automatic local backups (`backup-latest`, `backup-import`, `backup-reset`) and corrupt-data quarantine copies.
- Not persisted at all: selected "Fokus-Schutz" apps (DOM state only, static app names), the selected photo
  (in memory as a `Blob` until cleared or the page is closed).
- Leaves the device only on explicit user action: goal text, Mission type, confidence, optional schedule
  values (incl. time-zone name) and an optional photo → `/create-mission`; Mission-scoped context and
  feedback → `/adapt-mission`; a backup file the user downloads.
- Third-party transfers on every app start (before v17.17): Google Fonts (fonts.googleapis.com/gstatic.com),
  unpkg.com, cdn.tailwindcss.com — each receives the user's IP address and a Referer/Origin.

## 3. Trust boundaries

1. **Device owner ↔ app**: the user controls `localStorage`, DevTools, system time and the JS bundle.
   All local data (Missions, History, streak, achievements) is user-editable. Not a vulnerability; a design fact.
2. **Backup file ↔ app**: an imported file may come from anyone (shared backup, crafted file). Treated as hostile.
3. **AI Worker / Gemini response ↔ app**: model output can be influenced by prompt injection inside
   user-supplied material (e.g. a photographed worksheet). Treated as untrusted data.
4. **Browser ↔ Worker**: the Worker is a public, budget-consuming endpoint. Origin/CORS is not authentication.
5. **App ↔ third-party CDNs**: any CDN script runs with full privileges in the app origin.
6. **Future server-authoritative data** (verified completion, League score, entitlements, quotas) — does not
   exist yet and must never be sourced from local data or backups. See
   [`future-auth-and-verified-data.md`](future-auth-and-verified-data.md).

## 4. External services

| Service | Purpose | Data | Before v17.17 | v17.17 |
|---|---|---|---|---|
| GitHub Pages | Static hosting | IP, request metadata (host logs) | yes | unchanged |
| Cloudflare Workers (`*.workers.dev`) | AI proxy | Goal text, photo, schedule, Mission context, IP | on user action | unchanged (contract frozen) |
| Google Gemini API | Plan generation (via Worker) | Prompt content incl. photo | via Worker | unchanged |
| cdn.tailwindcss.com | Runtime CSS generator | IP, Origin/Referer | every start | unchanged — accepted risk (F-06) |
| unpkg.com (Lucide `@latest`) | Icons | IP, Origin/Referer | every start | **removed** (vendored, pinned 1.76.0) |
| Google Fonts | Inter font | IP, Referer, User-Agent | every start | **removed** (self-hosted) |
| Analytics / tracking | — | — | none found | none |

## 5. Entry points

Import file (`#dataImportFileInput`), Mission goal text (`#goalPrompt`), photo (`#missionFileInput`), schedule
picker, session feedback (adaptation), Worker responses (`create-mission`, `adapt-mission`), local storage
contents at boot (startup migration), service-worker install/update, cached responses. No URL parameters or
hash routes are read by the app (`location.search`/`hash` unused); no `postMessage`, `window.open`, `eval`,
`new Function` or string timers were found.

## 6. Threat actors

Ordinary user, curious technical user, malicious importer / author of a shared backup, author of
prompt-injecting study material, anonymous abuser of the public AI endpoint (cost exhaustion), compromised
CDN / dependency, future authenticated attacker. Details: [`threat-model.md`](threat-model.md).

## 7. Assets

Gemini API key and budget, Worker availability, user Mission/notes/feedback data, Session History, local
backups, application integrity (scripts executing in the origin), update integrity (service worker),
future account identity, ranks and entitlements.

## 8. Attack surface (observed)

- 86 `innerHTML`/`insertAdjacentHTML` sinks; almost all escape dynamic values with one of five local helpers
  (`escapeHTML`, `esc`, `escapeText`, `barrierEsc`, data-management `escapeHTML`).
- ~158 inline event handlers in `index.html` plus generated `onclick="fn('<id>')"` strings in
  `mission-management.js` — IDs are placed into a **JavaScript string inside an HTML attribute**, where HTML
  escaping does not help. Safety therefore depends on IDs having a safe character set.
- Import parses arbitrary JSON and spreads unknown fields into runtime records.
- Service worker caches broadly (any same-origin GET, any cross-origin GET incl. opaque responses).

## 9. Privacy data flow

```
User input ──► localStorage (plaintext, same-origin)  ──► optional JSON export (user-controlled file)
     │
     └─(explicit "Plan erstellen")──► Worker (Cloudflare) ──► Gemini (Google) ──► plan JSON ──► localStorage
App start (pre-v17.17) ──► Google Fonts, unpkg, Tailwind CDN  (IP address, Referer)
```

Payload audit of `/create-mission` (`analyzeGoal`): `goalText`, `missionType`, `confidence`, optional
`schedule` (`currentLocalDate`, `deadlineDate`, `preparationStartDate`, `preparationDays`, `startMode`,
`timezone`, `preferredWeekdays`, `preferredSessionMinutes`), optional `image`. **No** History, streak,
previous Missions, blocked apps, installation ID, settings or device data are sent. Minor excess: the original
photo filename and, for small photos sent unmodified, embedded EXIF metadata (possibly GPS) — fixed (F-09).

Payload audit of `/adapt-mission` (`requestAdaptation`): Mission-scoped only (title, subject, deadline text,
current step, remaining steps, this Mission's progress history incl. earlier free-text feedback, rating,
minutes). No global History/streak/apps/device data. Remaining steps include internal timestamps/IDs which are
not needed by a model; trimming them is a Worker contract change and is deferred (documented).

## 10.–16. Findings

Severity follows impact × realistic exploitability for the **current** local-first demo. "v17.17" states what
this version actually did.

| ID | Finding | Sev. | Exploitability | Impact | Location | Recommended correction | v17.17 | Remaining risk |
|---|---|---|---|---|---|---|---|---|
| F-01 | **AI-supplied step IDs reach inline `onclick` JavaScript.** `normalizeAIPlan` keeps `String(s.id)`, `normalizeUpdatedSessions` keeps `String(x.id)`, `normalizeMissionV115` keeps any non-empty `id`. IDs are later concatenated into `onclick="prepareLearningSession('<missionId>','<stepId>')"` and similar strings. A model response containing `x');…;('` executes script in the app origin and persists (stored XSS). | Medium | Requires the AI output to contain a crafted ID: prompt injection via photographed/pasted third-party material, or a compromised/misconfigured Worker. | Full script execution in origin: read/modify/exfiltrate all local data. | `src/services/ai-service.js` `normalizeAIPlan`; `src/features/optional-adaptation/optional-adaptation.js` `normalizeUpdatedSessions`; `src/repositories/mission-repository.js` `normalizeMissionV115`; sinks in `src/features/missions/mission-management.js` (`renderMissionHeroV116`, mission card actions) | Validate IDs at every entry boundary against the existing safe-ID policy (`schema.isValidEntityId`), regenerate unsafe ones and remap references; restrict exfiltration with CSP. | **Implemented** (AI boundaries + repository with reference remap; CSP `connect-src`/`img-src`). Tested. | Inline-handler architecture remains; safety relies on the ID policy. |
| F-02 | **Import has no size, depth, count, length or dangerous-key limits; unknown fields are copied into runtime state.** `prepareImportFromFile` calls `file.text()` on any size; normalisation spreads `...clone(m)`/`...clone(s)`. `JSON.parse` + spread/structuredClone create *own* `__proto__` properties (no global prototype pollution was reproducible), but such keys were persisted. | Medium | Any user who opens a crafted/shared backup. | Tab memory/CPU exhaustion, storage filled to quota, junk/hostile keys persisted and re-exported. | `src/data/data-import.js`; `src/data/migration-runner.js` `normalizeDatasetAndBuildMappings` | Byte limit before reading, structural scan (depth, nodes, keys, arrays, strings, dangerous and reserved keys), domain limits, mission/step field whitelist. | **Implemented** in the single validator (`data-validator.js` `inspectUntrustedStructure`, `validateImportLimits`) + whitelist in import normalisation. Tested. | Session records keep unknown *scalar-safe* fields within limits for forward compatibility (documented policy). |
| F-03 | **Import/restore not transactional under storage failure.** `applyBundleAtomically` ignores `setJSON`/`setText` results; a quota error mid-write leaves Missions replaced but History old and still reports success. The pre-import backup result is ignored. | Medium | Large dataset near quota, private-mode storage limits. | Silent partial data replacement / data loss. | `src/data/migration-runner.js` `applyBundleAtomically`, `createBackup`; `src/data/data-import.js` `confirmPreparedImport` | Check every write, read back and verify, roll back to the in-memory snapshot; abort before changes if the pre-import backup cannot be stored. | **Implemented**. Tested with simulated quota/write failure. | Rollback itself can fail if storage becomes unavailable mid-operation; reported as `rollback_failed`. |
| F-04 | **Unpinned third-party script** `https://unpkg.com/lucide@latest` without SRI executes in the origin; any new upstream release (or CDN compromise) runs with full privileges; also cached by the SW. | Medium | Supply-chain compromise or breaking release upstream. | Full script execution in origin. | `index.html` `<head>`; `service-worker.js` `EXTERNAL_CDN_URLS` | Pin and verify, preferably self-host. | **Implemented**: vendored `lucide@1.76.0` (`vendor/lucide/`, ISC licence, SHA-256 recorded) — identical to what `@latest` served at audit time. | Manual update process (documented). |
| F-05 | **Google Fonts loaded on every start** → IP address, User-Agent and Referer sent to Google without information or need (cf. LG München I, 20.01.2022, 3 O 17493/20). | Medium (privacy) | Automatic, every user. | Unnecessary third-country data transfer. | `index.html` `<head>` | Self-host the font. | **Implemented**: Inter variable (OFL) self-hosted under `assets/fonts/inter/`. | — |
| F-06 | **Tailwind Play CDN at runtime**: third-party script with full privileges, unpinned (latest 3.x), no SRI possible (no CORS), vendor marks it "not for production"; IP transfer to the CDN. | Medium | Supply-chain compromise of the CDN. | Full script execution in origin. | `index.html` `<head>` | Replace with a pre-built static CSS file (requires a one-off build or a vetted vendored stylesheet and visual regression). | **Deferred / accepted** (visual-regression risk too high for this version); CSP restricts it to the exact host. | Accepted for demo stage; required before public beta. |
| F-07 | **No Content-Security-Policy.** Any injected script could load remote code or exfiltrate via `fetch`/images. Static hosting cannot send headers; 158 inline handlers prevent a nonce/hash-based `script-src`. | Medium | Precondition: an injection bug (e.g. F-01). | Amplifies any XSS. | `index.html` | Strongest compatible policy via `<meta http-equiv>`; plan migration away from inline handlers; real headers via a proxy layer. | **Partially implemented**: meta CSP (`default-src 'self'`, explicit `connect-src`/`img-src`/`font-src`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `worker-src 'self'`, `manifest-src 'self'`); `script-src` still needs `'unsafe-inline'`. | `frame-ancestors`, `Permissions-Policy`, `X-Content-Type-Options` header cannot be set via meta on GitHub Pages. |
| F-08 | **Service-worker caching too broad**: caches any same-origin GET (incl. `docs/`, `.claude/`, `tests/` if requested), any cross-origin GET incl. opaque responses; Worker bypass uses `hostname.includes('workers.dev')`. | Low | Requires requests to those paths; opaque caching can pin a broken/poisoned CDN response. | Storage bloat, stale/poisoned third-party responses, development files cached on devices. | `service-worker.js` `fetch` handler | Allowlist runtime-cacheable paths/extensions and cross-origin hosts; exact Worker host suffix check; never cache non-200/non-basic. | **Implemented**; cache name bumped. Tested. | — |
| F-09 | **Photo metadata and filename sent to AI**: images ≤ 350 KB and ≤ 1800 px were uploaded unmodified (EXIF incl. possible GPS); original filename (may contain names) sent. | Low (privacy) | Every small photo upload. | Unnecessary personal data to Worker/Gemini. | `src/services/ai-service.js` `compressMissionImage`, `analyzeGoal` | Always re-encode through canvas (drops metadata), send a generic filename. | **Implemented**. | Visible content of the photo is, by design, sent. |
| F-10 | **Attachment validation trusts browser MIME/extension**; no signature check; no decoded-dimension limit before decoding (up to 20 MB input → potential decompression bomb). | Low | User selects a hostile file. | Tab crash / memory exhaustion. | `src/services/ai-service.js` `validateMissionImageInput`, `loadDrawableImage` | Magic-byte check, MIME/extension consistency, header-declared dimension and pixel limits before decode, post-decode check. | **Implemented**. Tested (unit + browser). | Browser image decoders remain the parser of record. |
| F-11 | **Cancel-then-retry race**: an aborted request's `finally` cleared the in-flight lock of the *new* request → possible concurrent duplicate AI calls / double billing. | Low | Fast cancel + retry. | Duplicate upstream cost; confusing state. | `src/services/ai-service.js` `analyzeGoal`, `cancelMissionAnalysis` | Per-request token; only the current request may mutate shared state. | **Implemented**. | Server-side duplicate detection needs the Worker (deferred). |
| F-12 | **AI response sizes unbounded**: strings and arrays from the model are stored verbatim. | Low | Misbehaving/injected model output. | Storage bloat, UI degradation. | `src/services/ai-service.js` `normalizeAIPlan`; `optional-adaptation.js` `normalizeUpdatedSessions` | Length and count caps. | **Implemented**. | — |
| F-13 | **Export integrity weak**: FNV-1a checksum over non-canonical JSON, computed before sanitisation and never verified. | Low | Accidental corruption. | Undetected damaged backups. | `src/data/migration-runner.js` `collectCurrentDataBundle`, `data-export.js` | SHA-256 over canonical JSON of `data`, verified on import; status shown in preview. Corruption detection only. | **Implemented**. Tested. | Anyone can recompute the digest — not tamper-proof, not proof of honest data. |
| F-14 | **No full local deletion**: "Zurücksetzen" deliberately keeps automatic backups (with Mission text) in `localStorage` indefinitely. | Low (privacy) | — | User cannot erase all app data from inside the app. | `src/data/data-reset.js` | Add explicit "Alles löschen (inkl. Backups)". | **Implemented**. Tested. | Browser-level storage/caches remain under browser control. |
| F-15 | **No transparency about AI processing**; no privacy notice or Impressum page. | Low (privacy/legal) | — | Users cannot tell what leaves the device. | `index.html` goal screen | Inline expandable note before generation; draft privacy notice. | **Implemented** (note) / **Documented** (draft notice requires legal review). | Legal texts not production-ready. |
| F-16 | **Internal error text can reach the UI** (`errors.join(', ')` of validation codes, `error.message` from exceptions, AI `fallbackError.message`). | Low | Error paths. | Information disclosure (minor), confusing UX. | `data-management-controller.js`, `migration-runner.js`, `ai-service.js` | Map codes to concise German messages; show request ID only when useful. | **Implemented**. | — |
| F-17 | **Direct `localStorage` access outside the storage gateway** in legacy session/recovery/mission modules; several `setItem` calls are not wrapped and can throw on quota during a session. | Low | Full storage. | Uncaught exception in a session flow. | `session-model.js` `setActiveSession`, `session-finalization.js` `saveSessionSnapshot`, `session-runtime.js`, `recovery-controller.js`, `mission-repository.js` `migrateLegacyMissionV115`, `bootstrap.js`, `ai-service.js` | Migrate to `App.services.storage` incrementally with behaviour tests of the timer/recovery flows. | **Documented / deferred** (timer & recovery are critical flows; gateway gained quota classification for the migrated callers). | Low. |
| F-18 | Blocked-apps popup concatenated app names without escaping (names are static markup today). | Info | Not exploitable now. | — | `src/features/quick-focus/quick-focus.js` `showBlockedAppsPopup` | Use text nodes. | **Implemented** (defence in depth). | — |
| F-19 | Legacy history loader resets corrupt History to empty after a single-slot quarantine copy (a later corruption overwrites the earlier copy). | Low | Repeated corruption. | Possible loss of an older corrupt copy. | `src/repositories/session-repository.js` `loadHistoryStore` | Multi-slot quarantine or no reset. | **Documented / deferred**. | Low. |
| F-20 | **Worker not verifiable**: source not in repo; endpoint unreachable from sandbox. Budget abuse (no accounts, public endpoint) is potentially High if rate limiting is absent. | Unverified (potentially High) | Anonymous scripted requests. | Gemini cost exhaustion, Worker unavailability. | Cloudflare dashboard / `worker.js` (external) | Origin allowlist, method/content-type/body limits, schema validation before upstream, bounded retry, rate limiting rules, budget alerts, redacted logs. | **Documented** as deployment requirements: [`worker-hardening-requirements.md`](worker-hardening-requirements.md). | Unknown until the owner verifies. |
| F-21 | Local streak, History and achievements are user-editable; League is a static demo. | Info | Trivial (DevTools/import). | None today (no competitive value). | `focus-insights.js`, `league.js` | Never use local data for competitive/paid features; reject reserved server-authoritative fields on import. | **Implemented** (reserved-field rejection, trust classes in schema registry, wording) + **Documented**. | By design. |
| F-22 | Free-text user data is stored in plaintext `localStorage`; automatic backups duplicate it up to three times. | Info | Local access / same-origin script. | Disclosure on a shared/compromised device. | storage layer | Document honestly; offer full deletion; no fake client-side encryption. | **Documented** + F-14. | By design for a local-first PWA. |
| F-23 | Static host cannot send security headers (CSP header, `frame-ancestors`, `Permissions-Policy`, `nosniff`); clickjacking protection unavailable. GitHub Pages enforces HTTPS and `github.io` is HSTS-preloaded. | Low | Framing by a hostile site. | UI redress on a demo with no sensitive actions. | hosting | Serve through a layer that can set headers (e.g. Cloudflare in front of a custom domain) before public beta. | **Documented**. | Accepted for demo. |
| F-24 | No secrets found in tracked files or the full Git history (patterns: Google API keys, OpenAI/GitHub tokens, private keys, bearer tokens, `.env`/credential files). Only variable *names* (`GEMINI_API_KEY`, `ALLOWED_ORIGIN`) appear in docs. | Info | — | — | repository | Keep `.gitignore` for env/credential files. | **Implemented** (`.gitignore`). | — |
| F-25 | **Duplicate step IDs in AI output crashed Mission creation**: `normalizeMissionV115` kept duplicates, `persistMissionStoreV115` threw `mission_store_invalid` uncaught from `confirmAIPlan`. Found while testing F-01. | Low | Model returns repeated IDs. | Plan cannot be saved; uncaught exception. | `mission-repository.js` | Unique-ID enforcement at the AI and repository boundary. | **Implemented** (same fix as F-01). Tested. | — |
| F-26 | **Shared origin on GitHub Pages**: a project site at `<account>.github.io/<repo>` shares its origin with every other Pages project of the same account. Any script on another of those pages can read/modify Focus Mode+ `localStorage`, and a service worker registered by the account's root site could control this path. | Low | Requires another (possibly experimental or compromised) Pages project under the same account. | Read/modify local data; interfere with offline cache. | hosting | Serve from a dedicated origin (custom domain or subdomain) before public beta. App code only touches its own key prefixes. | **Documented**. | Accepted for demo; required before public beta. |

### Evidence

- F-01 was reproduced on the unmodified v17.16 build in Chromium: a plan step with ID `x');window.__xss='F01';('`
  produced `onclick="…prepareLearningSession('<id>','x');window.__xss='F01';('')"` and invoking that handler
  executed the injected statement. On v17.17 no such handler exists (`tests/security/browser-security.test.js`).
- The same browser suite run against v17.16 fails 12/12; against v17.17 it passes 12/12. The Node suites cover
  the import, integrity, rollback, AI-boundary and service-worker findings.

## 17. Prioritisation

1. F-01 (stored script injection path) and F-03 (silent partial import) — correctness/safety of user data.
2. F-02, F-13 — hostile import handling, preview and integrity.
3. F-04, F-05, F-07, F-08 — supply chain, privacy transfers, CSP, service worker.
4. F-09–F-12, F-14–F-16, F-18 — attachments, AI client robustness, privacy controls, error hygiene.
5. F-06, F-17, F-19, F-20, F-23 — documented with explicit owner actions (blocked by deployment access,
   Worker ownership or regression risk).

## 18. Accepted risks (current demo stage)

- Local data is user-controlled and not trustworthy for any competitive or paid purpose.
- `script-src 'unsafe-inline'` remains until inline handlers are migrated.
- Tailwind Play CDN remains a third-party runtime dependency.
- Worker abuse controls are unverified.
- Draft legal texts are not reviewed.
