# PWA ↔ Android parity (v18)

The PWA (repository root, v17.17) is the reference for product behavior. This file tracks every
material difference. The screen-by-screen visual comparison of v18.1 is in
[visual-parity-v18.1.md](visual-parity-v18.1.md).

## Implemented

| Area | Android | PWA reference |
|---|---|---|
| Quick Focus | task (default "Konzentriert arbeiten", title = task), 5–60 min in steps of 5, static `MM:00`, one duration owner, one launch per tap | `quick-focus.js`, `startConfiguredQuickFocus` |
| Session types | `quick` never touches Missions; `mission` requires a valid Mission and step link (validated in the model and the database) | `session-model.js` |
| Finalize once | one History record per Session id (`INSERT IGNORE` + transaction); terminal statuses completed/cancelled/interrupted | `saveFinalSessionOnceV112` |
| < 1 minute | an end below one counted minute is cancelled (`ended_too_early`); Recovery cannot save below 1 min | `endSessionByUser`, `renderSessionRecovery` |
| Recovery choices | Fortsetzen / Fokuszeit speichern / Verwerfen (discard writes no History) | `recovery-controller.js` |
| Mission review | Mission Sessions wait for the step review; a step completes only on "completed"; never twice | `openRecoveredMissionReview`, `completeStep` |
| Mission rules | at most two active Missions; only the current step can start; safe ids `[A-Za-z0-9_.:-]{2,160}` | `mission-repository.js` |
| Mission identity tones | FNV-1a id hash + collision avoidance, verified against the PWA function | `resolveMissionThemesV177` |
| History | Heute / Woche (Monday) / Alle, newest first, PWA badge labels | `session-history.js` |
| Local streak | counted days (≥ 1 min, countAsFocus), current from today or yesterday, best run | `focus-insights.js` |
| Visual identity | cream, graphite, aubergine surfaces, purple accent, restrained green, Inter, floating nav with raised Plus, Cosmic Core | `styles/*`, `cosmic-core.js` |
| Motion tokens | 90/160/350/390/720 ms, standard/exit/soft-spring easings, reduced durations | `styles/motion.css` |
| Backup import | `focusmode-backup` v1 + legacy shape, v17.17 limits, unverified trust | `data-import.js`, security baseline §3 |
| Worker contract | `/create-mission` multipart fields, timeouts, error mapping, request id | `ai-service.js` |

## Partially implemented

| Area | State |
|---|---|
| Missions | list, detail, start the current step, review, archive/delete. **No creation UI**: the network client, validation and tests exist, but the goal → analyzing → confirm flow is deferred. Missions come from the backup import. |
| AI adaptation | `/adapt-mission` is declared in the API only; adaptation after a review is not offered. |
| Reminders for Missions | only for Missions that have `deadlineDate` (an ISO deadline text) or `preparationStartDate`. |
| Data export | not implemented (import only). |

## Deferred

Mission creation UI, AI adaptation UI, photo upload with on-device downscaling, Leagues and score
(server-authoritative in the future), Mission calendar, Focus insights screen, Operator animations,
home pull-refresh, managed-device enforcement, an explicit `AutomaticZenRule` DND integration,
instrumented and device tests, a formatting check in CI (`.editorconfig` only).

## Intentionally different

| Topic | PWA | Android | Why |
|---|---|---|---|
| Closing the app during a Session | Document termination freezes the timer; the user resumes later | The Session keeps running in real time (foreground service + ongoing notification); only a **reboot** freezes it at the last checkpoint | A native app can keep a visible Session alive; a web page cannot |
| Early end outcome | quick Session ended early ≥ 1 min → outcome `completed` | outcome `partial` (counts as focus) | The task asks to distinguish completed, partial and canceled; the PWA badge "Teilweise abgeschlossen" already exists |
| Quick Focus result | returns to Home | shows a Result screen (duration, outcome, local streak) | vertical-slice requirement |
| "Apps blockiert" | the PWA shows "N Apps blockiert" (a web app cannot block anything) | never claims blocking: "Ablenkungen überwachen", "App-Auswahl vorbereitet", "Strikte Sperre auf diesem Gerät nicht verfügbar" | honesty; Android consumer apps cannot block |
| Protected Focus end barrier | — | 10-second pause + reason, emergency exit always available | Android protection level |
| Emergency exit | — | counts focused time when ≥ 1 min (`emergency_exit`) | an emergency must not be punished |
| Session timer clock | `Date.now()` | monotonic `elapsedRealtime` + boot counter | survives sleep and wall-clock changes |
| Language | DE + EN | German only | v18 scope |
| Distracting apps | a fixed illustrative list | the user's real launchable apps, stored locally | native capability |
| Session actions | "Blockierung testen" next to "Sitzung beenden" | "Sitzung beenden" only | nothing is blocked on consumer Android, so there is nothing to test |
