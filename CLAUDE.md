# CLAUDE.md — Focus Mode+

Durable instructions for development sessions. Code is the source of truth; `docs/*` describe v14.1 and are partly stale — verify against code before relying on them.

## Project type
- Static vanilla HTML/CSS/JS PWA, hosted on GitHub Pages (subpath). No build step, no bundler, no `package.json`.
- Tailwind and Lucide load from CDNs. Scripts are classic `<script>` tags (not ES modules), loaded in a strict order from `index.html`, sharing the `FocusModeApp` namespace and some globals.
- UI language is German. Keep existing strings and tone.
- AI backend is a separate Cloudflare Worker (`workers.dev`, `/create-mission`, `/adapt-mission`). Never change Worker code, endpoints or request/response contracts from here.

## Hard prohibitions
- No migration to React, Vite, TypeScript or any framework. No `package.json`, `App.tsx`, `main.tsx` or preview apps.
- No parallel implementations: extend the existing owner, never add a second navigator, repository, storage layer or motion system.
- No full rewrites or reformatting of working files. No restoring old versions of `index.html` or modules.
- No redesign unless the task explicitly asks for it.

## Structure and owners
- `index.html` — markup for all screens (`#app-screen-{name}`), overlays, and the ordered script list.
- `styles/` — `app.css` (base), `navigation.css`, `missions.css`, `timer.css`, `recovery.css`, `quick-focus.css`, `data-management.css`, `motion.css` (motion tokens and layering).
- `src/core/` — `architecture-shell.js`, `bootstrap.js`, `runtime-ownership.js`, `navigation.js`, `motion-controller.js`.
- `src/repositories/` — `mission-repository.js`, `session-repository.js`.
- `src/features/` — `missions/`, `focus-session/`, `history/`, `recovery/`, `quick-focus/`, `optional-adaptation/`, `data-management/`.
- `src/services/` — `storage-service.js`, `ai-service.js`. `src/data/` — schema versions, validator, migrations, export, import, reset.

| Concern | Owner |
|---|---|
| Navigation | `src/core/navigation.js` (`navState`, `ROOT_SECTIONS`, `SCREEN_TO_ROOT_SECTION`, `LOGICAL_PARENT_MAP`, `navigateForward`, `navigateBack`). `setAppScreen` is wrapped in layers; the final wrapper is `recovery/recovery-router.js`. Always go through these, never toggle `.hidden` on screens directly. |
| Root screens | `home`, `missions` (maps to the `plan` screen; there is no `app-screen-missions`), `history`, `settings`. Bottom nav ids: `bottomNavHome/Missions/Plus/History/Settings`. |
| Child screens | stats and score belong to history, data and premium to settings, plan to missions; goal, quicksetup, filepicker, confirm and distractions are flow screens. |
| Overlays | `screen-popup-*`, the focus-choice overlay and plus sheet (opened via `MotionController.openPlusSheet/closePlusSheet`), the PWA install help, and the modal. |
| Mission data | `repositories/mission-repository.js` (key `focus-mode-missions-v11-5`). Logic: `features/missions/mission-management.js`, `mission-controller.js`. UI: `mission-renderer.js`, `multi-mission-ui.js`. |
| Session History | `repositories/session-repository.js` (key `focusmode-session-history-v11-1`; also `focusmode-active-session-v11`, `focusmode-last-session-v11`). Rendering and stats: `features/history/session-history.js`. |
| Storage | Raw `localStorage` goes through the repositories and `services/storage-service.js`. Import, export, reset and migrations live in `src/data/`. Never invent new keys without a migration/validator entry. |
| Motion | `src/core/motion-controller.js` (`window.MotionController`) with tokens in `styles/motion.css`. |

## Approved visual system (preserve; do not redesign)
- Warm cream background on normal planning and information screens.
- Deep aubergine Mission and summary surfaces; purple is the primary interaction accent.
- Restrained green only for active, ready and completed states. Dark graphite typography.
- Dimensional rounded cards with clean shadows; floating rounded bottom navigation.
- Home is the primary visual reference. The active Cosmic Timer stays a dark space environment.
- Do not restore the old slate-blue visual language. Do not redesign unrelated screens during focused tasks.
- Reuse existing classes and motion tokens; put new styles in the owning stylesheet, not inline.

## Critical flows that must stay compatible
- **Quick Focus** (`type: 'quick'`) never touches missions, steps or the active mission.
- **Mission Session** (`type: 'mission'`) requires valid `missionId` and `missionStepId`; the session repository rejects it otherwise.
- A session is finalized exactly once; History holds at most one record per `session.id`.
- Session recovery after reload resumes with exact elapsed time; an ended session below 1 minute is cancelled, otherwise finalized.
- Multi-mission: several missions, one selected/primary, with isolation between them. Mission creation through the AI plan flow (goal, analyzing, plan, confirm).
- AI adaptation is optional and skippable. Failure or offline must not block the user and must allow retry or continue.
- Export/import/reset and stored data stay backward compatible (schema versions and migrations).

## Large `index.html` rules
- Never read or rewrite the whole file. Locate with `grep -n 'id="app-screen-…"'` or a script id, then read only that line range.
- Make surgical, anchored edits. Preserve script order, ids and `data-*` hooks that JS depends on.
- A new script must be added to `index.html` (at the correct position) and to the service worker `CORE` list.

## Focused reading
- Start from the owner in the table above; read only the function or range being changed, plus direct callers found by grep.
- Do not audit the repo or read every file.
- Do not read all of `index.html` or `src/core/motion-controller.js` by default. First search exact screen IDs, function names, event handlers and CSS classes, then open only the relevant ranges. Read a complete large file only when a proven cross-cutting issue requires it.
- `docs/V15_ARCHITECTURE_PLAN.md` is obsolete historical material, not a setup or implementation instruction. Never create `package.json`, Vite, React or `src/main.js` from it. Treat `CHANGELOG` and regression reports as history too.
- Current source code overrides historical documentation whenever they conflict.
- Several functions are redefined by later scripts. Before editing one, grep for all definitions and edit the final owner.

## Navigation rules
- Logical root order: home → missions → history → settings.
- Plus is an action (opens the plus sheet), not a root page.
- `missions` maps to the existing `plan` screen; there is no `app-screen-missions` element.
- Root-page swipe and inner-screen swipe-back are separate mechanisms.
- The Home Mission carousel owns horizontal gestures that begin inside it.
- One route change has exactly one motion owner. Do not run CSS screen transitions, Web Animations and View Transitions together for one navigation.
- One Back gesture completes the authoritative Back action exactly once. Never call internal Back and `history.back()` for the same gesture.

## Motion and gesture rules
- Motion belongs to `motion-controller.js` and `motion.css` tokens (`--motion-*`, `--ease-*`). Do not add a competing animation system or ad-hoc timers.
- Animate `transform` and `opacity` only. No layout-thrashing, no blur-heavy new effects.
- Honor `prefers-reduced-motion` (short or zero durations) and guard against interrupted transitions (tokens and cleanup states).
- Edge swipe-back has a 28px edge threshold and a block list (inputs, `[data-block-swipe-back="true"]`, carousels). Respect it; new horizontally scrollable elements must opt out.
- Gestures must not conflict with vertical scrolling, carousels or the plus sheet.

## PWA and GitHub Pages constraints
- All paths are relative (`./…`), and the manifest `scope` and `start_url` are `./`. Never use root-absolute paths.
- `service-worker.js` has a versioned `CACHE` name (prefix `focus-mode`) and a hardcoded `CORE` precache list. When adding, removing or renaming a cached file or changing shipped behavior, update `CORE` and bump `CACHE` so clients refresh.
- Worker (`workers.dev`) requests must never be cached or intercepted.
- The app must keep working offline for everything except AI calls.

## Testing rules
- No test framework exists. Validate with `node --check` on edited JS files and by serving statically (`python3 -m http.server`) and exercising flows with the preinstalled Playwright/Chromium at a mobile viewport.
- Check the console for errors, and regress the affected flows from the section above. See `docs/REGRESSION_CHECKLIST.md` for a starting list.
- Test the PWA path too: service worker registers, offline reload works, and install/standalone display is unaffected.
- Report honestly what was and was not verified.

## Git discipline
- Work only on the branch assigned for the session; never push elsewhere (or to `production`) without explicit permission.
- Before editing: check `git branch --show-current` and `git status --short`, and decide the allowed file list for the task.
- Minimal diffs; one concern per commit with a clear message. Commit and push only when asked. Do not open PRs unless asked.
- Never skip or delete tests or checks to get green. Never force-push or rewrite others' history.
