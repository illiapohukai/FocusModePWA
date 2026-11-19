# Focus Mode+ — Data Inventory and Privacy Data Flow (v17.17)

Technical inventory from the source code. Legal assessments are open questions for a qualified reviewer
(marked **[LEGAL REVIEW]**); nothing here is legal advice. Storage keys are the real keys in
`src/data/schema-versions.js` unless noted.

## A. Local by default (never leaves the device unless the user exports or sends it)

| Data | Example | Source | Purpose | Storage (key) | Personal data possible? | Leaves device? | Retention | Deletion | Export | Import | Minimisation decision |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Missions | Title "Biologie Klausur", subject, type, deadline text, status, timestamps | User + AI plan | Plan and track goals | `focus-mode-missions-v11-5` | Yes (free text can contain names, school, health topics) | Only Mission-scoped fields to `/adapt-mission` on explicit "Plan anpassen"; in backups | Until user deletes / browser storage cleared | Archive/delete Mission; "Zurücksetzen"; "Alle lokalen Daten löschen" | Yes | Yes (validated, unknown fields dropped) | Needed for the feature |
| Mission steps / tasks | "Kapitel 3 zusammenfassen", reason, tools, recommended apps, durations | AI plan, adaptation | Step-by-step plan | inside Missions | Yes (free text) | As above | As above | As above | Yes | Yes | Needed |
| AI source analysis | detected tasks/pages, facts, estimates, open questions | AI response | Explain plan | inside Missions (`sourceAnalysis`, `estimate`) | Possibly (derived from user material) | No (only back in backups) | As Missions | As Missions | Yes | Yes (whitelisted, bounded) | Kept: shown in briefing |
| Mission progress history | outcome, rating, free-text feedback, minutes, `stepId` | Session review | Adapt plan, progress | inside Missions (`progress.history`) | Yes (feedback text) | To `/adapt-mission` (this Mission only) on explicit request | As Missions | As Missions | Yes | Yes | Needed for adaptation |
| Session History | type, title/task, planned/actual minutes, start/end timestamps, outcome | Timer | History, stats, local streak, calendar | `focusmode-session-history-v11-1` (≤ 500 records) | Yes (titles, behaviour pattern/time of day) | No | Rolling 500 newest | "Zurücksetzen"; "Alle lokalen Daten löschen" | Full backup only | Yes (≤ 5000 accepted, newest 500 kept) | Capped at 500 |
| Active / last Session | running Session incl. checkpoint timestamps | Timer | Crash/reload recovery, review | `focusmode-active-session-v11`, `focusmode-last-session-v11` | Yes | No | Until finalized/replaced | Finalization; reset; delete all | Full backup only | Yes | Needed for recovery |
| Local streak, totals, achievements, Activity, calendar | "3 Tage in Folge", week minutes, calendar dots | **Derived** at render time from History/Missions | Motivation | Not stored (computed in `focus-insights.js`, `mission-calendar.js`, `league.js`) | Derived | No | n/a | Follows source data | Implicitly (source data) | Implicitly | Not stored separately. **Not verified, user-editable.** |
| League preview | "Gold League", sample names | Static demo constant in `league.js` | Feature preview | Code only | No (fictional sample names) | No | n/a | n/a | No | No | Labelled as demo |
| Selected blocked apps ("Fokus-Schutz") | "Instagram", "TikTok" | Static app list in markup, user selection | Barrier UI during Session | **DOM state only — not persisted** | Low (indicates app usage) | No | Page lifetime | Page close | No | No | Not stored at all |
| Selected photo | worksheet photo | User file/camera | Send to AI for plan | Memory only (`Blob`), preview `blob:` URL | Yes (visible content; metadata stripped since v17.17) | Yes, on "Mission planen" | Until removed, replaced, plan created or page closed; object URLs revoked | "×" on file card; page close | No | No | Re-encoded, ≤ 1800 px, generic filename |
| Last AI plan | Normalised plan JSON | AI response | Restore pending plan | `focus-mode-last-ai-plan` | Yes | No | Until next plan / reset | Reset; delete all | Yes | Yes | Kept for recovery of an unconfirmed plan |
| Settings | language `de`/`en`, PWA-help dismissed, onboarding flag | User | UI behaviour | `focus-mode-language`, `focus-pwa-help-v11`, `focus-mode-home-onboarding-v17-16` | No | No | Until changed/deleted | Delete all (language kept by "Zurücksetzen") | Language, PWA flag | Language, PWA flag | Minimal |
| Schema / migration state | versions, fingerprints, ID maps | App | Safe migrations | `focus-mode-data-schema-v16`, `focus-mode-migration-state-v16` | Indirect (IDs) | No | Until delete all | Delete all | **No (excluded since v17.17)** | No | Internal only |
| Automatic local backups | Full copy of the data above incl. raw snapshot | App before migration/import/reset | Undo / restore | `focus-mode-backup-latest-v16`, `-import-v16`, `-reset-v16` | Yes (copies of all free text) | No | Until overwritten or "Alle lokalen Daten löschen" (kept by "Zurücksetzen" by design) | Delete all | No | Restore button | Up to three copies — documented; full deletion added |
| Quarantine copies | Corrupt raw JSON | App on parse failure | Forensics/recovery | `*-corrupt-backup`, `*-repository-backup`, `*-recovery-backup` | Yes | No | Until overwritten / delete all | Delete all | No | No | Single slot each |
| Legacy keys | pre-v11.5 Mission/progress | Older versions | Migration | `focus-mode-active-mission`, `focus-mode-mission-progress-v7`, `focusmode-session-history-v11` | Yes | No | Until migration/reset | Reset; delete all | `legacyCompatibility` section | Yes (legacy path) | Migrated, then unused |
| Service-worker cache | Static app files only | App | Offline use | Cache Storage `focus-mode-v17-17-…` | No user data | No | Until next version | Automatic on update; browser site-data clearing | No | No | Allowlisted static assets only |

No cookies, IndexedDB, `sessionStorage`, analytics, tracking pixels, fingerprinting or installation IDs are
used.

## B. Sent to the AI only when required (explicit user action)

| Request | Trigger | Fields sent | Not sent | Destination | Notes |
|---|---|---|---|---|---|
| `POST /create-mission` (multipart) | "Mission planen" | `goalText`, `missionType`, `confidence`, optional `schedule` {`currentLocalDate`, `deadlineDate`, `preparationStartDate`, `preparationDays`, `startMode`, `timezone`, `preferredWeekdays`, `preferredSessionMinutes`}, optional `image` (re-encoded, generic filename) | History, streak, other Missions, blocked apps, settings, device data, installation ID | Cloudflare Worker → Google Gemini | Time-zone name (e.g. `Europe/Berlin`) is needed for correct schedule dates. IP address and User-Agent reach Cloudflare as with any HTTPS request. |
| `POST /adapt-mission` (JSON) | "Plan anpassen" after a Mission Session | `missionId`, `missionUpdatedAt`, Mission title/subject/deadline text, current step, planned/actual minutes, outcome, rating, free-text feedback, remaining steps, this Mission's progress history | Other Missions, global History, streak, apps, settings, device data | Cloudflare Worker → Google Gemini | Remaining steps include internal IDs/timestamps the model does not need; trimming is a Worker contract change → deferred (documented). Skipping adaptation is always possible. |

Users are informed on the goal screen ("Was wird an die AI gesendet?") before sending.

## C. Third parties contacted without user action

| Service | When | Data | v17.17 status |
|---|---|---|---|
| Hosting (GitHub Pages) | Every load | IP, User-Agent, requested URLs | Unavoidable for a website |
| cdn.tailwindcss.com | Every online start (cached by SW afterwards) | IP, Origin/Referer (origin only), User-Agent | **Remains** — replacement planned (F-06) |
| Google Fonts | — | — | **Removed** (self-hosted Inter) |
| unpkg.com | — | — | **Removed** (vendored Lucide) |

## D. Lawful-basis questions **[LEGAL REVIEW]**

- Local storage of user-entered content on the user's own device: is this processing by the provider at all
  (no access by the provider) and does § 25 TTDSG/TDDDG apply to `localStorage` as "strictly necessary"?
- AI processing: performance of a service requested by the user (Art. 6(1)(b) GDPR) vs. consent; special
  categories (Art. 9) may appear in free text or photos (e.g. health topics) — how is this handled?
- Transfers to Google/Cloudflare (third countries): safeguards (DPF certification, SCCs) to be confirmed.
- Minors: is the app aimed at pupils under 16 (Art. 8 GDPR)?
- Operational logs at the Worker: purpose, retention, legal basis.
