# Changelog — Focus Mode+

## v17.3.1 Motion System (2026-11-10)

### Motion System, Splash Screen & Transition Architecture
- **Startup Splash Screen (`#fmStartupSplash` in `index.html`, `styles/motion.css`, `src/core/motion.js`)**:
  - Added an immediate full-viewport warm-cream (`#F6F1E7`) startup splash screen using the existing `./icon-192.png` app icon, `FOCUS MODE+` wordmark, and restrained `Wird vorbereitet …` status indicator.
  - Coordinated splash dismissal with `resolveInitialAppStateV112` and the authoritative recovery router so interrupted sessions open directly into `#app-screen-session-recovery` without flashing Home or showing a white frame.
  - Added error/rejection and timeout fail-safes (`1380ms` hard ceiling) so the splash can never remain stuck.

- **Global Directional Screen Transitions (`src/core/motion.js`, `styles/motion.css`)**:
  - Integrated hardware-accelerated (`transform`, `opacity`) outgoing and incoming transitions into `applyAppScreenImmediately` without delaying synchronous state updates, storage writes, or recovery locks:
    - **Forward navigation**: Outgoing screen fades and shifts `-12px` X while incoming screen enters from `+18px` X (`240ms–250ms`, `--ease-enter`).
    - **Backward navigation**: Reversed horizontal motion (`+12px` outgoing, `-16px` incoming, `220ms–240ms`).
    - **Root-tab navigation** (`Home`, `Missionen`, `Verlauf`, `Profil`): Short crossfade with subtle `4px–6px` vertical motion (`170ms–200ms`) while keeping the floating bottom dock stable.
    - **Modal/overlay screens** (`distractions`, `filepicker`, `confirm`, `update`): Restrained upward entrance and subtle background scale/fade.
  - Added transition pointer-event protection, rapid-tap interruption cleanup, timeout fail-safes, and `visibilitychange` reset.

- **Focus-Session Start & Exit Transitions (`#fmFocusLaunchPortal`, `#app-screen-timer`)**:
  - Added a radial dark-violet launch portal (`#fmFocusLaunchPortal`) expanding from the pressed Start button into the `#050714` Cosmic Timer surface (`480ms`).
  - Added a restrained staged reveal inside `#app-screen-timer` (header metadata, cosmic planet/orbit rings, timer digits, protection pill, focus task card, and bottom actions) while starting the session timer immediately according to authoritative session logic.
  - Added smooth session exit contraction/fade into `#app-screen-result` (for Mission steps, preserving Optional Adaptation) or `#app-screen-home` (for Quick Focus).

- **Floating Navigation, Central Plus Action & Three-Dot Mission Menus**:
  - Animated active tab indicator pill, icon elevation, and press feedback on `#bottomNavigationV17`.
  - Animated `#bottomNavPlus` press compression and `45deg` icon rotation when `#focusChoiceOverlay` is open, paired with a warm-cream bottom sheet slide/fade transition.
  - Animated all three-dot Mission menus (`#missionSheetV116`) with trigger button press feedback, bottom-sheet scale/slide entrance and exit (`185ms–220ms`), subtle action item stagger, keyboard `Escape` support, focus transfer on open, and focus restoration to `.mmc-menu` on close.

- **Mission Carousel, Mission Plan, Activity, History & Reduced Motion**:
  - Preserved native horizontal scroll-snap on `#missionCarouselV1181` while adding smooth scale/opacity emphasis between the centered card (`scale(1)`) and adjacent card (`scale(0.97)`, `opacity: 0.92`).
  - Enhanced `toggleFutureStepV81` to smoothly scroll later Mission steps (`index > 0`) into view while keeping step 1 stationary.
  - Added one-time initial Home Activity entrance stagger (`.fm-activity-initial-enter`), smooth single-row insertion (`.fm-activity-new-item`) when a new session is recorded, list crossfades on History/Mission filter switches, and concise expandable details on `.history-card` tap.
  - Full `@media (prefers-reduced-motion: reduce)` support disabling translations, scaling, radial expansion, and staged animations in favor of short opacity transitions (`<= 100ms`).
  - Updated `service-worker.js` cache to `focus-mode-v17-3-1-motion-system` and cached `./styles/motion.css` and `./src/core/motion.js`.

---

## v17.3 Home System (2026-11-10)

### Visual & Interface Architecture Redesign
- **Home Screen (`#app-screen-home`)**:
  - Redesigned Home around the warm editorial cream palette (`#F6F1E7` background, `#FCF9F4` cards, `#E7DFD2` borders, `#6D28D9` primary violet CTA) and one dark aubergine Focus Mission hero block (`#25162B`).
  - Added compact Home header with `Focus Mode+` brand mark, subtitle `Starte fokussiert in den Tag`, and top-right `Verlauf` pill button.
  - Rebuilt `#activeMissionHeroCard` and `.mission-carousel-card`:
    - Supports `0` active Missions (`#emptyMissionCard`), `1` active Mission (`.single-mission`), and `2` active Missions (`.multi-mission` with horizontal `scroll-snap-type: x mandatory`, right-edge card peek, and interactive pagination dots).
  - Replaced the second large Quick Focus card with a compact Quick Focus row (`#quickFocusCompactCard`), a 3-column `HEUTE` statistics grid, and the `AKTIVITÄT` timeline.
  - Refined the slim 5-item floating bottom navigation dock (`Start`, `Missionen`, central `+` button, `Verlauf`, `Profil`).
