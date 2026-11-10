# Changelog — Focus Mode+

## v17.3 Home System (2026-11-10)

### Visual & Interface Architecture Redesign
- **Home Screen (`#app-screen-home`)**:
  - Redesigned Home around the warm editorial cream palette (`#F6F1E7` background, `#FCF9F4` cards, `#E7DFD2` borders, `#6D28D9` primary violet CTA) and one dark aubergine Focus Mission hero block (`#25162B`).
  - Added compact Home header with `Focus Mode+` brand mark, subtitle `Starte fokussiert in den Tag`, and top-right `Verlauf` pill button.
  - Rebuilt `#activeMissionHeroCard` and `.mission-carousel-card` in `src/features/missions.js` and `styles/navigation.css`:
    - Supports `0` active Missions (`#emptyMissionCard`), `1` active Mission (`.single-mission`), and `2` active Missions (`.multi-mission` with horizontal `scroll-snap-type: x mandatory`, right-edge card peek, and interactive pagination dots).
    - Redesigned `.mh-duration-box` (min-width `68px`, height `60px`, cream `#F6F1E7` badge on dark aubergine surface) so `5`, `25`, `35`, `40`, and `120` minute durations fit cleanly without clipping or overflow.
    - Full-width violet primary CTA (`Mission fortsetzen` / `Schritt starten`) and secondary inline text links (`Plan öffnen`, `Nächste Mission`).
  - Replaced the second large Quick Focus card with a compact Quick Focus row (`#quickFocusCompactCard`) and a 3-column `HEUTE` statistics grid (`Sessions`, `Fokuszeit`, `Erfolgsquote`) backed by `SessionHistoryRepository`, followed by a centered `Verlauf ansehen` link.
  - Refined the slim 5-item bottom navigation bar (`Home`, `Verlauf`, central `+` button, `Missionen`, `Einstellungen`).

- **Secondary Screens Harmonization**:
  - **Mission Plan (`#app-screen-plan`)**: Updated header card to the dark aubergine surface with progress bar and structured step cards (Completed green, Current violet highlight, Upcoming neutral).
  - **Quick Focus Setup (`#app-screen-setup`) & Mission Creation (`#app-screen-mission-Setup`)**: Warm cream layout, dark aubergine duration display card, clean preset chips, and unified 44×44px top-left back buttons (`.fm-top-back-btn`).
  - **App Blocking Selection (`#app-screen-select`)**: Added dark aubergine session summary block (`#selectSummaryBlock`), category pills, and clear selected-app counters.
  - **Session History (`#app-screen-history`)**: Added dark aubergine overview block (`#historyOverviewCard`) with real session metrics (`Gesamt`, `Fokuszeit`, `Erfolg`), clean filter pills, and v17.3 history cards.
  - **Mission Management (`#app-screen-missions`)**: Added dark aubergine overview block (`#missionOverviewCardV173`) showing active slot count (`X / 2 aktiv`), redesigned active/completed/archived Mission cards, and preserved all mission actions (continue, open plan, set primary, rename, archive, restore, delete).
  - **Settings (`#app-screen-settings`) & Data/Backup (`#app-screen-data`)**: Grouped into 5 clean sections (`Fokus & Sitzung`, `Missionen & Verlauf`, `Sprache`, `Daten & Backup`, `Über die App`) with version updated to `v17.3 Home System`.
  - **Session Evaluation & Optional Adaptation (`#app-screen-check`, `#app-screen-adapt`, `#app-screen-complete`, `#sessionRecoveryOverlay`)**: Styled with warm editorial surfaces while preserving finalized-session protection and optional AI adaptation fallback.
  - **Cosmic Timer (`#app-screen-timer`)**: Preserved the dark cosmic timer environment (`styles/timer.css`, `ultraCosmicV91`) untouched.

- **Navigation & Offline PWA Stability**:
  - Prevented legacy `updateActiveChapter()` from mutating `.app-screen` classes in `pwa-app` mode (`src/core/navigation.js`).
  - Ensured `navigateToRoot('history')` refreshes `renderSessionHistory()` and `navigateToRoot('home')` refreshes `refreshHomeStats()`.
  - Updated `service-worker.js` cache key to `focus-mode-pwa-v17-3-home-system`.
