# REGRESSION_REPORT.md — v17.4.1 Mission Generation Repair

## 1. Scope & Honest Test Boundaries
- **Verified locally (real headless Chromium + Node.js)**:
  - Pure logic unit tests (`node --test`).
  - JavaScript syntax validation (`node --check`) across all `.js` files.
  - Real Chromium browser verification across `390×844`, `320×640`, `430×932`, and `390×500` viewports for layout, calendar bottom sheet viewport attachment, scroll lock/restore, `Zeitplan` board & preferences, image validation/compression/removal, double-tap/cancellation state management, error recovery/input preservation, core navigation tabs, Quick Focus, and Cosmic Timer.
  - Network responses for `POST /create-mission` were **mocked in Chromium via Playwright `page.route()`** (`524 {}`, `429` structured JSON error, malformed payload, slow pending request cancellation, and valid scheduled plan response).
- **Not executed / Out of scope without Worker source**:
  - Live Cloudflare Worker deployment (`https://focusmodedemoai.illia-pohukai.workers.dev/create-mission`) and live Gemini API generation could **not** be executed because the Cloudflare Worker source code was not included in the input ZIP (documented in `WORKER_REQUIRED.md`).
  - Native iOS Safari / installed iOS PWA and Android WebView runtime hardware tests were not executed in this Linux container environment (`visualViewport` clamping and `safe-area-inset-bottom` CSS rules were verified via DOM/CSS and short-viewport checks).

---

## 2. Unit Tests (`node:test`)
5 / 5 passed:
1. `validateMissionImageInput`: rejects non-allowed MIME types (`application/pdf`), empty files (`0 B`), files `> 20 MB`, and accepts valid JPEG/PNG/WEBP files.
2. `buildMissionSchedulePayload`: omits `schedule` when no `deadlineDate` is set; serializes required (`currentLocalDate`, `deadlineDate`, `preparationStartDate`, `preparationDays`, `startMode`, `timezone`) and optional (`preferredWeekdays`, `preferredSessionMinutes`) fields cleanly.
3. `parseMissionErrorResponse`: cleanly formats `{ error: { code, message, retryable } }`, string errors, and HTTP `524`/`429`/`503` failures without `[object Object]`.
4. `normalizeAIPlan`: validates goal title, 1–6 valid sessions, 5–60 min step bounds, rejects sessions with `scheduledDate > deadlineDate`, and preserves `scheduleWarning`.
5. `validateMissionScheduleState`: rejects past deadlines, start-after-deadline custom selections, and zero-matching-weekday ranges.

---

## 3. Browser Verification (Headless Chromium via Playwright)
### Calendar Bottom Sheet Positioning & Scroll Stability
- **DOM Ownership**: `#missionCalendarSheet.parentElement === document.body` (outside `#app-screen-goal` and transformed `#appBody`).
- **Fixed Viewport Geometry**: Tested at `390×844`, `320×640`, `430×932`, and `390×500` across top, middle, and bottom scroll positions of `#app-screen-goal`. Verified backdrop `position: fixed`, `inset: 0`, full viewport width & height, `.mg-cal-sheet` anchored flush at `bottom === window.innerHeight`, zero horizontal overflow (`scrollWidth <= innerWidth`).
- **Scroll & Focus Restoration**: Verified exact `scrollTop` preservation while locked and exact restoration after `Cancel`, backdrop tap, and `Escape` (after reverse closing animation), plus focus restoration to `#mgDeadlineTrigger` (`preventScroll: true`).

### Mission Creation `ZEITPLAN` & Responsive Visual Integrity
- Verified empty and active 3-layer `ZEITPLAN` board (`START` -> trajectory nodes -> `ZIEL`, `7 Tage Vorbereitung`, expandable preferences accordion with weekday toggles `Mo..So` / `Mon..Sun`, quick presets, and duration grid).
- Verified `320px` mobile width (`320×640`) renders with zero horizontal overflow (`docW: 320`, `goalScrollW: 320`, `schedScrollW: 280`) and zero truncation on `"1 Woche vorher"` / `"EMPFOHLEN"`.
- Verified `I18N` language registration works cleanly in both `DE` and `EN`.

### Mission Generation, Image Pipeline & Mocked Network Flows (`page.route`)
- **File Validation & Removal**: `.pdf` rejected inline; valid `.png` displays `#selectedFileCard` and clears cleanly via `#clearMissionFileBtn`.
- **Client Image Compression**: A `2400×1600` (`424 KB`) PNG compressed automatically to JPEG `<= 1.6 MB` and attached to `FormData` under field `image`.
- **Single-Flight & Cancellation**: Rapid double-tap triggered exactly 1 `POST /create-mission` request (`isMissionAnalyzeInFlight` lock active); `#cancelAnalysisBtn` aborted in-flight request, released lock, and returned cleanly to `#app-screen-goal`.
- **Error Recovery & Input Preservation**: Mocked HTTP `524` (`{}`) and `429` (`{ error: { code, message, retryable } }`) showed structured inline `.mg-error-card` (no `[object Object]`), dismissed `#app-screen-analyzing` cleanly, and preserved `goalPrompt`, attached image, and `schedule` state.
- **Schedule Payload & Plan Rendering**: Confirmed `FormData` omits `schedule` and `image` when not provided, includes valid `schedule` JSON when deadline is selected, validates and renders `#app-screen-confirm` and `#app-screen-plan` (`#aiPlanSchedulePreview`), and releases image `ObjectURL` cleanly on confirm.

### Core Regression Flows
- Verified Home screen rendering, bottom dock navigation across `Home`, `Missionen`, `Verlauf`, `Einstellungen`, and `Quick Focus -> #app-screen-timer` with zero uncaught `pageerror` exceptions.
