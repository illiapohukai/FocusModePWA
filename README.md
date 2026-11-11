# Focus Mode+ · v17.4 Mission Scheduling UI

**v17.4 Mission Scheduling UI** erweitert **Focus Mode+ v17.3.2 Fluid Motion System** um vier gezielte Verbesserungen:
1. Wiederherstellung der markanten zweizeiligen Home-Header-Typografie (`#homeCurrentDate` in Versalien über dem kräftigen Produkttitel `Focus Mode+`).
2. Ausgewogene vertikale Textpositionierung innerhalb der leeren Home-Missionskarte (`#emptyMissionCard`).
3. Korrekte Positionierung des `Fokusschutz aktiv`-Indikators (`.uc-protect`) direkt unterhalb des Cosmic-Timer-Planeten und oberhalb der Aufgabenkarte (`#ucFocusCard`).
4. Neuer `ZEITPLAN`-Bereich (`#missionScheduleSection`) in der Missionserstellung (`#app-screen-goal`) inklusive eigenem Kalender-Bottom-Sheet (`#missionCalendarSheet`), Vorbereitungsstart-Empfehlungen, visueller Vorbereitungs-Timeline, optionaler Lerntags-Auswahl (`Mo–So` + Schnell-Presets), bevorzugter Sitzungsdauer und clientseitiger Validierung.

---

## Projektstruktur

- `index.html` — Haupt-App-Shell & Screens
- `styles/` — Stylesheets (`app.css`, `timer.css`, `recovery.css`, `quick-focus.css`, `missions.css`, `data-management.css`, `navigation.css`, `motion.css`)
- `src/` — Modulare JavaScript-Architektur (`core/`, `data/`, `features/`, `repositories/`, `services/`)
- `service-worker.js` & `manifest.webmanifest` — Offline-fähige PWA-Konfiguration
- `docs/` — Projektdokumentation (`CHANGELOG.md`, `REGRESSION_REPORT.md`)

## Lokaler Start

Da das Projekt reines HTML, CSS und Vanilla JavaScript ohne Build-System verwendet, kann es direkt über jeden statischen Webserver gestartet werden:

```bash
npx serve . -l 3000
# oder
python3 -m http.server 3000
```
