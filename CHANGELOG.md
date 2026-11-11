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
- **Startup Splash & First-Screen Choreography**:
  - Added startup splash overlay (`#fmStartupSplash`) with cosmic emblem pulse (`520ms–1150ms`), 220ms exit dissolve, and staggered entrance of the initial screen (`Home` or `Session Recovery`) and floating bottom dock (`#bottomNavigationV17`).
- **Directional Root Tab & Hierarchical Push/Pop Transitions**:
  - Root tab transitions (`Home`, `Missionen`, `Verlauf`, `Einstellungen`) animate directionally with distance-scaled travel and duration (`distance 1: 24px/250ms`, `distance 2: 32px/280ms`, `distance 3: 40px/300ms`).
  - Same-tab tap smoothly scrolls the active root screen container to top and pulses the active tab icon.
  - Forward push transitions (`Home/Missionen -> Goal`, `Home -> Quick Focus`, `Quick Focus/Plan/Settings -> Distractions`, `Verlauf -> Statistik -> Score`, `Einstellungen -> Daten & Backup`, `Einstellungen -> Premium`) and Back pop transitions animate with layered depth and parallax while keeping the floating bottom dock anchored.
- **Shared-Element & Morph Transitions**:
  - **Central Plus (`#bottomNavPlus`) <-> Focus Choice Sheet (`#focusChoiceOverlay`)**: Spatial origin morph from the central purple `+` button into the bottom sheet with 45° icon rotation, backdrop dim, and staggered option cards; reverses cleanly into `#bottomNavPlus` on close, backdrop click, or `Escape`.
  - **Home/Mission Card <-> Mission Plan (`#app-screen-plan`)**: Hero surface morph proxy between the aubergine Mission card and the Mission Plan header with staggered step cascade; reverses back to the originating surface (`Home` or `Missionen`).
  - **Setup/Plan CTA -> Cosmic Timer (`#app-screen-timer`)**: Shared planet seed expansion from the start CTA into the Cosmic Timer `.uc-planet` (`620ms`), orbit ring reveal, and green protection pill (`#ucProtectBadge`) pulse.
  - **Cosmic Timer -> Home (`focus-exit-home`)**: Planet contraction trajectory from `.uc-planet` into the central `#bottomNavPlus` button with dock reception pulse and new `.home-activity-row` highlight.
  - **Cosmic Timer -> Result -> Adaptation/Update -> Home**: Smooth post-session evaluation, progress-bar fill, and learning flow transitions.
- **Interactive Micro-Motions & Accessibility**:
  - Goal Intake mission type cards animate via `transform`, `box-shadow`, and `border-color` only (zero `width`/`height`/`font-size` layout shift) with confidence progress bar choreography.
  - Mission Plan step cards expand/collapse smoothly using measured height interpolation while keeping the card top in view.
  - Full `prefers-reduced-motion: reduce` compliance (instant/100ms opacity-only crossfades, no spatial morphs or stagger delays) and clean Web Animations API fallback when `document.startViewTransition` is unavailable.
  - Updated `service-worker.js` cache key to `focus-mode-pwa-v17-3-2-fluid-motion-system` with `./styles/motion.css` and `./src/core/motion-controller.js` pre-cached.

## v17.3 Home System (2026-11-10)

### Visual & Interface Architecture Redesign
- **Home Screen (`#app-screen-home`)**:
  - Redesigned Home around the warm editorial cream palette (`#F6F1E7` background, `#FCF9F4` cards, `#E7DFD2` borders, `#6D28D9` primary violet CTA) and one dark aubergine Focus Mission hero block (`#25162B`).
  - Added compact Home header with `Focus Mode+` brand mark, subtitle `Starte fokussiert in den Tag`, and top-right `Verlauf` pill button.
  - Rebuilt `#activeMissionHeroCard` and `.mission-carousel-card` in `src/features/missions.js` and `styles/navigation.css`.
  - Replaced the second large Quick Focus card with a compact Quick Focus row (`#quickFocusCompactCard`) and a 3-column `HEUTE` statistics grid (`Sessions`, `Fokuszeit`, `Erfolgsquote`) backed by `SessionHistoryRepository`.
  - Refined the slim 5-item bottom navigation bar (`Home`, `Verlauf`, central `+` button, `Missionen`, `Einstellungen`).
