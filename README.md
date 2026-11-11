# Focus Mode+ · v17.3.2 Fluid Motion System

**v17.3.2 Fluid Motion System** erweitert **Focus Mode+ v17.3 Home System** um ein vollständiges, zuverlässiges und flüssiges Motion-System über die gesamte Anwendung hinweg, ohne das etablierte warme Editorial-Designsystem (`#F6F1E7` Creme-Hintergrund, `#25162B` Aubergine-Missionskarten, Floating-Dock, Cosmic Timer) oder die bestehende Geschäftslogik zu verändern.

---

## Wichtigste Neuerungen in v17.3.2 Fluid Motion System

1. **Zentraler MotionController (`src/core/motion-controller.js` & `styles/motion.css`)**
   - Deterministische Zustandsmaschine (`idle`, `preparing`, `animating`, `cleaning`) mit Transition-Lock, automatischem Timeout-Fallback und Bereinigung bei `pagehide`, `visibilitychange`, `Escape` sowie schnellen Mehrfach-Taps.
   - Nahtlose Integration in den bestehenden Navigations-Owner (`src/core/navigation.js`), sodass Geschäftslogik und Datenpersistenz niemals auf Animationen warten müssen.

2. **Startup-Choreografie & Splash-Übergang (`#fmStartupSplash`)**
   - Ruhiger Start-Splash (`520ms–1150ms`) mit sanftem Emblem-Puls, 220ms Ausblendung und gestaffelter Ankunft des ersten Screens (`Home` oder `Session Recovery`) sowie der unteren Navigationsleiste (`#bottomNavigationV17`).

3. **Gerichtete Root-Tab- & Hierarchische Push/Pop-Übergänge**
   - **Root-Tabs (`Home`, `Missionen`, `Verlauf`, `Einstellungen`)**: Gerichtete horizontale Übergänge mit distanzabhängiger Amplitude und Dauer (`Distanz 1: 24px/250ms`, `Distanz 2: 32px/280ms`, `Distanz 3: 40px/300ms`).
   - **Same-Tab-Tap**: Sanftes Scrollen zum Seitenanfang und dezenter Icon-Impuls bei erneutem Tippen auf den aktiven Tab.
   - **Push / Pop**: Tiefe- und Parallax-Animationen für alle Unterseiten (`Goal Intake`, `Quick Focus Setup`, `Distractions`, `Mission Plan`, `Statistik`, `Weekly League`, `Daten & Backup`, `Premium`) bei stabil verankertem Bottom-Dock.

4. **Shared-Element- & Morph-Übergänge**
   - **Zentraler Plus-Button (`#bottomNavPlus`) <-> Focus Choice Sheet (`#focusChoiceOverlay`)**: Räumlicher Ursprungs-Morph aus dem violetten `+`-Button mit 45°-Icon-Rotation, abgedunkeltem Hintergrund und gestaffelten Aktionskarten.
   - **Missionskarte <-> Mission Plan (`#app-screen-plan`)**: Kontinuierlicher Flächen-Morph zwischen der Aubergine-Missionskarte und dem Mission-Plan-Header.
   - **Fokus-Start -> Cosmic Timer (`#app-screen-timer`)**: Expansion eines kosmischen Planeten-Seeds aus dem Start-CTA in den zentralen `.uc-planet` (`620ms`) inklusive Orbit-Aufbau und grünem Schutz-Indikator (`#ucProtectBadge`).
   - **Cosmic Timer -> Home**: Rückführung des Planeten in den zentralen `#bottomNavPlus`-Button mit Dock-Puls und Hervorhebung des neuen Eintrags in der Aktivitäts-Timeline (`.home-activity-row`).

5. **Barrierefreiheit & Reduced Motion (`prefers-reduced-motion: reduce`)**
   - Vollständige Unterstützung für reduzierte Bewegung (kurze, ruhige Opazitäts-Überblendungen ohne räumliche Verschiebungen oder Stagger-Verzögerungen).

---

## Projektstruktur

- `index.html` — Haupt-App-Shell & Screens
- `styles/` — Stylesheets (`app.css`, `timer.css`, `recovery.css`, `quick-focus.css`, `missions.css`, `data-management.css`, `navigation.css`, `motion.css`)
- `src/` — Modulare JavaScript-Architektur (`core/`, `data/`, `features/`, `repositories/`, `services/`)
- `service-worker.js` & `manifest.webmanifest` — Offline-fähige PWA-Konfiguration
- `docs/` — Projektdokumentation

## Lokaler Start

Da das Projekt reines HTML, CSS und Vanilla JavaScript ohne Build-System verwendet, kann es direkt über jeden statischen Webserver gestartet werden:

```bash
npx serve . -l 3000
# oder
python3 -m http.server 3000
```
