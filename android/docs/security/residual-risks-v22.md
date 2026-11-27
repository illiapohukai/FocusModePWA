# Residual risks — v22

Risks that remain after v22, why they are accepted or deferred, and what would reduce them. None
is a known Critical or High vulnerability. "Owner" is the repository owner.

| ID | Risk | Rating | Why it remains | Reduction / trigger to revisit |
|---|---|---|---|---|
| R01 | Deployed Rules differ from the tested file (accidental permissive deployment, wrong project) | **High impact, unlikely** | Deployment is a manual owner step; the repository cannot see production | Deploy only `firebase/firestore.rules` after `npm run test:emulator`; add a CI deploy job gated by the Rules tests when a project exists; verify live (live-validation L05) |
| R02 | Live Google sign-in, real tokens and Firebase Auth behavior not validated | Medium | No OAuth client or Firebase project exists | Live validation L01–L04 before any pilot |
| R03 | App Check not enforced; Play Integrity verdicts not validated | Medium | Enforcement must follow verified metrics; no project | firebase-app-check-v21.md rollout; L06–L07 |
| R04 | Recursive server-side deletion depends on an undeployed Cloud Function | Medium | Needs the Blaze plan and owner deployment | account-deletion-v21.md; L08. The client already deletes every document it writes and reports "incomplete" otherwise |
| R05 | Client-written History, Missions and settings are self-reported (owner can rewrite, delete and re-create own data; list items unchecked by Rules) | Low (by design) | Rules cannot verify honesty; deleting is needed for account deletion | Never use client documents for League, rewards or entitlements; compute those server-side into server-only paths (future-verified-league.md) |
| R06 | Rooted or compromised device reads/changes the whole sandbox (Room, DataStore, Firebase session) | Medium on such devices | No app-level control protects data from the device owner; custom encryption would not help | Accept; tokens are Firebase-managed and revocable server-side; L10 |
| R07 | OS backups contain the Room database (incl. selected apps and the uid binding); `adb backup` on Android ≤ 11 with physical access and user consent | Low | Product decision (restore Missions and History) | Consider excluding `selected_apps` from cloud backup; L11 for OEM behavior |
| R08 | StrandHogg 2 / task hijacking on unpatched Android 8–10 | Low | minSdk 26; no app setting prevents it | Raise minSdk to 30 when the audience allows |
| R09 | No certificate pinning | Low | Cloudflare and Google rotate certificates without notice; a pin without rotation would cause outages | Revisit with a custom domain and a backup-pin rotation plan |
| R10 | No anti-tampering, root, hook or debugger detection | Low | All authorization is server-side; detection is bypassable and protects no server asset | Revisit only if a server-trusted client signal (League) is ever introduced — then use App Check/Play Integrity server-side |
| R11 | Screenshots and recents may show the email and Mission titles | Low | No secrets on screen; `FLAG_SECURE` would break support and pitch workflows | Revisit if a screen ever shows a secret (e.g. recovery codes) |
| R12 | Dependency advisories newer than the last CI run | Low | Advisories appear continuously | CI `security` job on every Android/Firebase change; allow-list entries expire |
| R13 | The `@grpc/grpc-js` override diverges from the Firebase JS SDK's declared range | Low | Needed until the SDK ships a fixed range | Remove the override when `@firebase/firestore` depends on ≥ 1.13.6; Rules tests and `npm audit` show regressions |
| R14 | Self-reported times inside the accepted window (2000 … now + 24 h) can still be false | Low | Plausible values cannot be distinguished from honest ones | Unverified data by design (R05) |
| R15 | A notification posted before the route key existed (older build) or after the key file was lost opens the app without navigating | Low (usability) | Fail-safe behavior of V22-F06 | Accept |
| R16 | No forced-update mechanism | Low | No store release yet | Add Play in-app updates at release |
| R17 | Live network interception and OEM-specific behavior (battery managers, backup transports) untested | Low | Needs physical devices and a proxy setup | live-validation L09–L12 |
| R18 | Old-id test installs keep their own sandbox data | Informational | Package ids cannot share data | Export/import and uninstall the old builds (application-identity-v22.md) |
| R19 | Gemini key and Worker abuse limits live outside this repository | Medium (external) | Worker code is not changed here by policy | Keep the key in the Worker only; Worker-side rate limits and size limits; never call Gemini from the app |

Nothing in this list is hidden from the user-facing documentation: R02–R04 are stated in the
Firebase setup guide, the App Check document and the account-deletion document.
