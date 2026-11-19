# Focus Mode+ — Threat Model (v17.17)

Scope: the static PWA in this repository, its service worker, local storage, backup files, and its calls to
the external Cloudflare Worker (`/create-mission`, `/adapt-mission`). The Worker itself is external; its
controls are requirements (see [`worker-hardening-requirements.md`](worker-hardening-requirements.md)).
Method: lightweight STRIDE per entry point, proportionate to a local-first demo with no accounts.

## Actors

| Actor | Capability | Motivation |
|---|---|---|
| Ordinary user | Uses the UI, imports own backups | Accidental damage (wrong file, full storage) |
| Curious technical user | DevTools, edits `localStorage`, crafts JSON | Inflate streak/History, explore |
| Malicious importer | Supplies a crafted backup file | Script injection, data destruction, DoS |
| Shared-backup attacker | Gets a victim to import "their" backup | Persist hostile content in the victim's app |
| Prompt-injection author | Writes text into study material the victim photographs/pastes | Influence AI output (IDs, text) |
| Public-endpoint abuser | Scripts requests to the Worker | Free AI usage, exhaust budget |
| Credit-exhaustion attacker | High-volume or oversized requests | Cost, availability |
| Local-storage manipulator | Same as curious user, or malware on device | Tamper with local state |
| Oversized-input supplier | Huge JSON / decompression-bomb image | Crash the tab |
| Compromised dependency | Controls a CDN script | Full code execution in origin |
| Future authenticated attacker | Valid account | Forge verified completions, ranks, entitlements |

## Assets

Gemini API access and budget · Worker availability · Mission data · notes/feedback · photos · Session History ·
settings · local backups · future account identity · future ranks and subscriptions · application integrity
(code running in the origin) · update integrity (service worker and cache).

## Entry points and threats

Legend — *Pre*: precondition. *Now*: mitigation in v17.17. *Residual*: remaining risk. *Future*: next step.

### E1 Import file (`#dataImportFileInput`)
| Threat | Pre | Now (v17.17) | Residual | Future |
|---|---|---|---|---|
| Persistent XSS via strings in titles/tasks | Victim imports file | All renderers escape text; IDs must match safe-ID policy; CSP limits exfiltration | Inline-handler architecture | Remove inline handlers, strict CSP |
| Prototype pollution via `__proto__`/`constructor`/`prototype` | Crafted keys | Recursive rejection before any processing; no recursive merges of untrusted objects | — | — |
| DoS via size/depth/arrays | Crafted file | 5 MB byte limit before reading, depth ≤ 20, node/key/array/string limits, domain limits | Parsing ≤ 5 MB on main thread (~tens of ms) | Worker-thread parsing if limits grow |
| Data destruction via partial import | Quota/storage failure | Validate fully first, pre-import backup required, checked writes, read-back verification, rollback | Rollback failure if storage disappears | — |
| Smuggling "verified"/server fields | Crafted file | Reserved server-authoritative field names rejected | — | Server never accepts local backups as evidence |
| Corrupt/truncated file | Accident | SHA-256 digest (if present) verified; "Datei beschädigt" | Files without digest (older exports) rely on schema validation | — |

### E2 Export file
| Threat | Now | Residual |
|---|---|---|
| Leaks personal content when shared | Privacy note before export; "ohne Sitzungsverlauf" option; filename without user data | User decides where the file goes |
| Secrets in export | No secrets exist client-side; exporter strips raw snapshot and credential-like preference keys | — |
| Misread as proof | Integrity labelled "Backup geprüft = Datei unverändert seit Export", explicitly not proof of honesty | — |

### E3 Mission/task/note text, E4 calendar dates, E5 AI responses
| Threat | Now | Residual | Future |
|---|---|---|---|
| HTML/script injection in renderers | Escaping helpers on all dynamic `innerHTML` paths; text nodes where touched | Five separate escape helpers | Consolidate into one DOM helper |
| JS-string injection through IDs in inline `onclick` | IDs validated/regenerated at AI, adaptation, repository and import boundaries | Architecture still builds handlers from strings | Event delegation with `data-*` |
| Oversized AI output | Length/count caps | — | Worker-side schema enforcement |
| Malformed dates | Dates only rendered after strict `YYYY-MM-DD`/timestamp parsing | — | — |

### E6 Uploaded image
| Threat | Now | Residual |
|---|---|---|
| Wrong type disguised by extension/MIME | Signature (magic bytes) must match JPEG/PNG/WebP and declared type | Browser decoder bugs |
| Decompression bomb | Header dimensions ≤ 12 000 px/side and ≤ 40 MP before decode; post-decode check; output ≤ 1800 px | — |
| Metadata leak (EXIF/GPS, filename) | Always re-encoded via canvas; generic upload filename | Visible content is sent by design |
| Retention | Held in memory only until cleared/replaced/page closed; object URLs revoked | — |

### E7 Route state / URL parameters
The app reads no URL parameters or hash routes; navigation state is in-memory (`navState`). Manifest
`start_url` carries a static cache-busting query only. No open-redirect or route-injection surface found.

### E8 Worker request body / E9 Worker response
| Threat | Pre | Now (client) | Required (Worker, external) |
|---|---|---|---|
| Budget exhaustion by scripted clients | Public endpoint | Client single-flight lock + per-request token (accidental duplicates only) | Rate limiting, body/attachment limits, schema validation before upstream, output-token cap, budget alerts |
| Double billing on double tap/cancel | UI race | Request token; one active request owner | Optional idempotency key in a coordinated contract change |
| Hostile/garbled response | Prompt injection, Worker bug | Strict normalisation, ID policy, caps, escaping | Response schema validation in Worker |
| Secret leakage | Worker logs or errors | Client never holds secrets | Redacted logging policy |

### E10 Service-worker updates / E11 cached files
| Threat | Now | Residual |
|---|---|---|
| Caching API responses or backups | Worker host and API paths bypassed; non-GET never handled; backups are local `blob:`/File reads, never fetched | — |
| Cache poisoning via opaque third-party responses | Cross-origin caching restricted to the Tailwind host; failed responses not cached | Tailwind CDN content itself is trusted |
| Development files cached (`docs/`, `.claude/`, `tests/`) | Not in `CORE`; runtime caching allowlisted by path/extension | — |
| Stale shell after update | Versioned `CACHE`, old caches deleted on activate, network-first navigation | One reload delay after deploy |

## Explicit non-goals (v17.17)

- No protection of local data against the device owner. Local streak/History are editable by design.
- No anti-debugging, obfuscation, client-side encryption with shipped keys, or client-side HMAC.
- No accounts, authentication, League backend or payments.

## Future corrections (summary)

Server-authoritative Session ledger and verified streak (see
[`future-auth-and-verified-data.md`](future-auth-and-verified-data.md)), Worker rate limiting and validation,
header-capable hosting, removal of inline handlers for a strict CSP, replacement of the Tailwind Play CDN.
