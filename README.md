# Focus Mode+ · v17.5.1 Interaction Fixes

**v17.5.1 Interaction Fixes** behebt sieben gezielte Layout- und Interaktionsprobleme auf Basis von **Focus Mode+ v17.5 Interaction Polish**:
1. Ausgewogene vertikale Ausrichtung des gesamten internen Inhaltsflusses der leeren Home-Missionskarte (`#emptyMissionCard` / `.home-hero-content`) um ca. 8–10 px nach oben.
2. Kontinuierliche aktive/inaktive Karten-Tiefenstaffelung (`scale`, `opacity`, `translateY`, Schatten) im 2-Missionen-Karussell (`#missionCarouselV1181.multi-mission`).
3. Fehlerfreie Öffnungsanimation der Missionsliste (`#app-screen-missions`) ohne rechten Viewport-Überlauf oder Einfrieren während des Übergangs.
4. Eindeutiges Ziel für „Missionen verwalten“ aus dem Plus-Panel (`Aktiv`-Tab wird deterministisch ausgewählt).
5. Bereinigung des Plus-Panels (`#focusChoiceOverlay`) durch Entfernen doppelter Missionszeilen (`BESTEHENDE MISSIONEN`).
6. Eliminierung vertikaler Layout-Sprünge beim Öffnen von Kurzer Fokus (`#app-screen-quicksetup`) und beim Start der Fokus-Sitzung (`#app-screen-timer` / `#ucFocusCard`).
7. Zuverlässige interaktive Swipe-Back-Navigation vom linken Bildschirmrand (`<= 28 px`) für gestapelte Unterbildschirme.

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
