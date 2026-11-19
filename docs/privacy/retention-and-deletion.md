# Focus Mode+ — Retention and Deletion (v17.17)

Describes actual current behaviour (from code) and the intended policy. Where they differ, it says so.

## Current behaviour

| Data | Where | Retention today | How the user deletes it |
|---|---|---|---|
| Missions, steps, progress, feedback | Browser `localStorage` | Until the user deletes it or clears site data | Delete/archive a Mission; Settings → Daten & Backup → "Lokale Daten zurücksetzen" or "Alle lokalen Daten löschen" |
| Session History | `localStorage` | Newest 500 records; older ones drop off automatically | Same as above |
| Active/last Session, last AI plan | `localStorage` | Until finalized/replaced | Same as above |
| Automatic backups (latest, pre-import, pre-reset) | `localStorage` | Until overwritten by the next backup of the same kind. **"Zurücksetzen" keeps them on purpose** (undo). | "Alle lokalen Daten löschen" (v17.17) or clearing site data |
| Corrupt-data quarantine copies | `localStorage` | Until overwritten | "Alle lokalen Daten löschen" |
| Settings (language, PWA hint, onboarding flag) | `localStorage` | Until changed | "Alle lokalen Daten löschen" (reset keeps language) |
| Selected photo | Memory | Until removed/replaced or the page is closed | "×" on the file card; closing the app |
| Selected blocked apps | DOM only | Page lifetime | Not stored |
| Exported backup file | User's device/cloud | Fully under user control | User deletes the file |
| AI request content (goal text, photo, schedule, Mission context) | Cloudflare Worker (transient), Google Gemini | Per provider configuration and terms **[TO BE CONFIRMED]** | Not deletable from the app; provider terms apply |
| Worker operational logs | Cloudflare | **Not verifiable from this repository** **[TO BE CONFIRMED]** | Operator configuration |
| Service-worker cache | Cache Storage | Static files only; old versions deleted on update | Browser site-data clearing |

"Alle lokalen Daten löschen" removes every `localStorage` key with the app prefixes (`focus-mode*`,
`focusmode-*`, `focus-pwa-*`, `focus_lang`) and nothing else; it stops a running Session first and asks for
explicit confirmation. It does not touch the service-worker cache, which holds no user data. Verified in
Chromium: immediately after deletion no app key remains; on the next start the existing startup migration
writes four metadata keys (schema state, migration state, "migrated" flag and an **empty** pre-migration
backup with 0 Missions / 0 Sessions) — none contains user content.

## Intended policy

1. Local data stays until the user deletes it or the browser clears storage. The app never deletes user data
   silently; the 500-record History cap is the only automatic pruning and is shown in the import preview.
2. Exported backups are the user's files; the app cannot and does not track them.
3. AI request content is processed only to answer the request. The operator must confirm with the providers
   that content is not retained longer than necessary or used for training **[TO BE CONFIRMED]**.
4. Application logs (Worker) contain no user content — only request ID, route, status class, latency, sizes
   and rate-limit decisions. Retention for operational metadata: **[TO BE DEFINED before production]**
   (suggested ≤ 30 days).
5. Future accounts: account deletion must remove server-side profile data, verified Session records,
   scores, entitlements and quotas, with documented exceptions for legally required records (e.g. invoices)
   **[LEGAL REVIEW]**. Local data on devices remains under user control and needs separate deletion.

## Gaps

- Worker/Gemini retention is unknown until confirmed (see `processors-and-transfers.md`).
- Up to three automatic backups duplicate local content; this is deliberate for undo and is now deletable.
