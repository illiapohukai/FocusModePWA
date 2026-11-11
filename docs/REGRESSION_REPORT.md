# Regression & Verification Report — v17.4 Mission Scheduling UI

## 1. Architecture, Boundary & Visual System Preservation
- **Original Structure Preserved**: `index.html`, `src/`, `styles/`, `docs/`, `manifest.webmanifest`, `service-worker.js`, and all root PWA icons remain in their exact root locations.
- **Backend & AI Contract Untouched**: `src/services/ai-service.js`, Cloudflare Worker endpoints, AI prompts, and `FormData` fields (`goalText`, `missionType`, `confidence`, `image`) remain 100% unmodified.
- **Syntax Validation**: `node --check` passed with 0 errors across all JavaScript files in `src/` and `service-worker.js`.

## 2. Playwright Chromium Verification Matrix (`v17.4 Mission Scheduling UI`)

| Category | Test Scenario | Result |
| :--- | :--- | :--- |
| **Home Header Typography** | First line `#homeCurrentDate` shows dynamic localized current date in uppercase (`MITTWOCH, 11. NOVEMBER` / `WEDNESDAY, NOVEMBER 11`); second line `#homeBrandTitle` shows `Focus Mode+` in heavy display weight (`font-weight: 900`, `36px–38.7px` on `390px–430px`); right-side `BEREIT` pill and profile icon button do not collide or wrap across `320px`, `375px`, `390px`, `430px`. | PASS |
| **Empty Mission Card** | Main text group (`h3.mh-empty-title` + `p.mh-empty-copy`) moved upward by `10px` (`titleOffset: 63px` vs `73px`), while `.mh-top-row` (`39px`), `.mh-empty-next-box` (`231px`), and overall `#emptyMissionCard` height (`330px`) remain unchanged. | PASS |
| **Cosmic Timer Fokus-Schutz** | `#app-screen-timer .uc-protect` (`Fokusschutz aktiv`) positioned directly below `.uc-planet` / `.uc-readout` (`protectTop: 365px > readoutBottom: 306px`) and above `#ucFocusCard` (`protectBottom: 412px <= taskCardTop: 431px`), horizontally centered (`protectCenterX: 195px` on `390px` viewport). | PASS |
| **Mission Creation Layout** | Upper Mission Creation sections (`Mission-Typ`, `#goalPrompt`, file upload, `Wie sicher fühlst du dich?`) preserved; vertical scrolling reaches `#missionScheduleSection` (`ZEITPLAN`) and `#missionPlanButton` (`Mission planen`) without overlap. | PASS |
| **Calendar Bottom Sheet** | Opens on `#mgDeadlineTrigger` or `Eigene Wahl`; renders Monday-first week (`Mo–So`); navigates months (`November 2026: 30d`, `Dezember 2026: 31d`, `Januar 2027: 31d`, `Februar 2027: 28d`, `Februar 2028 leap year: 29d`); disables past dates; highlights today and selected date; `Abbrechen` discards pending change; `Übernehmen` commits selection. | PASS |
| **Preparation Start & Timeline** | Auto-recommends `Sofort` (`1–7d`), `1 Woche vorher` (`8–21d`), `2 Wochen vorher` (`22–60d`), `Eigene Wahl` (`>60d`) without overriding manual user choice; clamps start date to today when offset precedes today; updates timeline labels and `X TAGE VORBEREITUNG`. | PASS |
| **Weekdays & Session Duration** | Individual toggles `Mo..So` (`1..7`), presets `Unter der Woche` (`[1,2,3,4,5]`), `Wochenende` (`[6,7]`), `Flexibel` (`[]`), and preferred session duration chips (`20 Min.`, `30 Min.`, `45 Min.`, `AI entscheidet`) update `window.missionScheduleState`. | PASS |
| **Client-Side Validation** | Past dates disabled; deadline today shows info (`Der Zeitplan beginnt heute.`); short prep shows warning (`Sehr wenig Vorbereitungszeit.`); start after deadline blocks submission with error (`Der Vorbereitungsstart muss vor der Prüfung oder Abgabe liegen.`); no matching weekday in range blocks submission with error (`Im gewählten Zeitraum liegt kein ausgewählter Lerntag.`); no deadline remains valid and optional. | PASS |
| **Core App Regressions** | Home with 0, 1, and 2 active Missions; Mission Management (`Aktiv`, `Abgeschlossen`, `Archiv`); Session History (`Heute`, `Diese Woche`, `Alle Sitzungen`); Statistics; Settings language switch (`DE` / `EN`); Backup & Restore (`Daten & Backup`). | PASS |
