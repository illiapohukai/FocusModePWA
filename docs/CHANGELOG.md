# Changelog — Focus Mode+

## v17.4 Mission Scheduling UI (2026-11-11)

### Home Header Typography Restoration, Empty Card Rhythm, Cosmic Timer Protection Pill & Mission Creation Scheduling UI
- **Home Header Typography Restoration (`index.html`, `styles/navigation.css`, `src/features/history/session-history.js`)**:
  - Restored the two-line display hierarchy on the Home header (`.home-header-v173`):
    - First line (`#homeCurrentDate`): Dynamic localized current date in uppercase small-cap style (`MITTWOCH, 11. NOVEMBER` / `WEDNESDAY, NOVEMBER 11`), `clamp(17px, 4.5vw, 22px)`, `font-weight: 600`, `letter-spacing: 0.03em`.
    - Second line (`#homeBrandTitle`): Product title `Focus Mode+` in heavy display weight (`font-weight: 900`, `clamp(36px, 9vw, 48px)`, `letter-spacing: -0.045em`, `line-height: 1.0`, `#14161B`).
    - Right side (`.home-header-right`): Preserved `BEREIT` readiness pill (`#homeReadyStatus`) with slightly stronger text weight (`#homeReadyStatusText`) and profile button (`.home-profile-btn`), aligned cleanly without wrapping across `320px–430px` viewports.
- **Empty Mission Card Text Position (`styles/missions.css`)**:
  - Lifted only the main text group (`h3.mh-empty-title` and `p.mh-empty-copy`) inside `#emptyMissionCard` upward by `10px` (`margin-top: 0px !important` with `+10px` compensatory `margin-top: 24px !important` on `.mh-empty-next-box`), keeping `.mh-top-row`, `BEREIT`, `ERSTER SCHRITT` / `AI PLAN`, and the overall card height unchanged.
- **Fokus-Schutz Position on the Cosmic Timer (`styles/motion.css`, `src/core/motion-controller.js`)**:
  - Restored `#app-screen-timer .uc-protect` (`Fokusschutz aktiv`) directly below the Cosmic Timer planet (`.uc-planet` / `.uc-readout`) and above `#ucFocusCard` (`position: absolute; left: 50%; bottom: 8px; transform: translateX(-50%); z-index: 10;`), and preserved `translateX(-50%)` across all Web Animations API keyframes in `animateFocusEnter`.
- **Mission Creation `ZEITPLAN` Scheduling UI & Calendar Bottom Sheet (`index.html`, `styles/missions.css`, `src/features/missions/mission-renderer.js`)**:
  - Added the `ZEITPLAN` section (`#missionScheduleSection`) below `.mg-confidence` and above `#missionPlanButton` inside the vertically scrollable Mission Creation screen (`#app-screen-goal`), keeping all upper sections and dimensions intact.
  - **Deadline Selector (`#mgDeadlineTrigger`) & Custom Calendar Bottom Sheet (`#missionCalendarSheet`)**: Custom warm-cream calendar bottom sheet with month/year navigation, Monday-first weekday layout (`Mo–So`), disabled past dates, today outline, purple selected date, range highlight, and `Abbrechen` / `Übernehmen` actions.
  - **Preparation Start (`#mgPrepStartBlock`)**: Selectable chips (`Sofort`, `1 Woche vorher`, `2 Wochen vorher`, `Eigene Wahl`) with non-overriding automatic recommendations based on days until deadline (`1–7d`, `8–21d`, `22–60d`, `>60d`) and automatic clamping to today when offset precedes today.
  - **Preparation Timeline (`#mgTimelineBox`)**: Dynamic start/deadline labels, purple progress connector (`START` — `PRÜFUNG`), and calculated `X TAGE VORBEREITUNG` badge (with neutral empty state before deadline selection).
  - **Available Weekdays (`#mgWeekdayRow`, `#mgWeekdayPresets`) & Preferred Session Duration (`#mgDurationPrefChips`)**: Multi-select weekday toggles (`Mo..So` = `1..7`), quick presets (`Unter der Woche`, `Wochenende`, `Flexibel`), and session duration chips (`20 Min.`, `30 Min.`, `45 Min.`, `AI entscheidet`).
  - **Client-Side Schedule State & Validation (`window.missionScheduleState`, `window.validateMissionScheduleState`)**: Inline validation for past deadlines, deadline today, short preparation window, start after deadline, and no selected weekday in the chosen range without `window.alert`, while keeping Cloudflare Worker endpoints, AI prompts, and `FormData` request payloads completely untouched.

## v17.3.2 Fluid Motion System (2026-11-11)

### Complete Fluid Motion System & Choreographed Transitions
- **Authoritative MotionController (`src/core/motion-controller.js` & `styles/motion.css`)**:
  - Implemented `window.MotionController` with a deterministic 4-state machine (`idle` -> `preparing` -> `animating` -> `cleaning` -> `idle`), automatic timeout release, and cleanup on `pagehide`, `visibilitychange`, `Escape`, and rapid interrupted navigation.
  - Preserved all v17.3 visual design tokens (`#F6F1E7` cream background, `#25162B` aubergine Mission cards, typography, layout, Cosmic Timer) and all business/routing logic in `src/core/navigation.js`.
