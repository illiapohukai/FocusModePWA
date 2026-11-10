# Focus Mode+ · v17.3 Home System

**v17.3 Home System** ist das visuelle und strukturelle Interface-Redesign von **Focus Mode+ v17.2**. Es führt das warme, ruhige Editorial-Designsystem aus der primären Designreferenz auf Home und allen Nicht-Timer-Oberflächen ein, während die gesamte bestehende Geschäftslogik, die lokalen Speicherformate (`localStorage`) und die dunkle kosmische Timer-Umgebung (`Cosmic Timer`) vollständig erhalten bleiben.

---

## Wichtigste Neuerungen in v17.3 Home System

1. **Warmes Editorial-Farbsystem & Typografie (`styles/navigation.css`)**
   - Ruhiger Creme-Hintergrund (`#F6F1E7`), edle helle Kartenflächen (`#FCF9F4`), dezente Rahmen (`#E7DFD2`) und klarer Kontrast mit maximal einem dunklen Aubergine-Block (`#25162B`) pro Screen.
   - Einheitliche Design-Tokens (`--bg-app`, `--surface`, `--surface-muted`, `--border`, `--text-primary`, `--text-secondary`, `--primary`, `--dark-card-bg`, `--success`, `--warning`, `--danger`).

2. **Neu gestalteter Home Screen (`#app-screen-home`)**
   - **Kompakter Header**: App-Brand-Marke (`Focus Mode+` + Untertitel `Starte fokussiert in den Tag`) links und kompaktes `Verlauf`-Pill rechts.
   - **Dunkler Aubergine Focus-Mission-Hero-Block (`#activeMissionHeroCard`)**:
     - Unterstützt `0`, `1` und `2` aktive Missionen mit horizontalem Swipe-Carousel (`scroll-snap-type: x mandatory`), sichtbarem rechten Peek für die zweite Mission und interaktiven Pager-Punkten (`1 / 2 aktiv`).
     - Großzügige Dauer-Badge (`.mh-duration-box`) für `5` bis `120+` Minuten ohne Überlauf oder Abschneiden.
     - Primärer violetter CTA (`Mission fortsetzen` / `Schritt starten`) und dezente Sekundär-Links (`Plan öffnen`, `Nächste Mission`).
   - **Kompakte Schnell-Fokus-Zeile (`#quickFocusCompactCard`)**: Sofortiger Einstieg in den freien Fokus-Modus ohne dominante zweite Hero-Karte.
   - **Heute-Statistik-Sektion**: 3-Spalten-Statistikraster (`Sessions`, `Fokuszeit`, `Erfolgsquote`) mit echten Daten aus `SessionHistoryRepository` sowie kompaktem Link `Verlauf ansehen` darunter.
   - **Schlanke untere Navigation (`#appBottomNav`)**: 4 Tabs (`Home`, `Verlauf`, `Missionen`, `Einstellungen`) plus hervorgehobener zentraler `+`-Action-Button für das Fokus-Auswahl-Sheet.

3. **Konsistente Sekundär-Screens im v17.3 Designsystem**
   - **Mission Plan (`#app-screen-plan`)**: Dunkler Aubergine Mission-Header, strukturierte Schrittkarten mit Status-Farben (Abgeschlossen, Aktueller Schritt, Geplant) und einheitlicher Sticky-Action-Bar.
   - **Schnell-Fokus Setup (`#app-screen-setup`)**: Ruhige Creme-Oberfläche mit dunklem Aubergine Dauer-Block, Preset-Pills (`15`, `25`, `45`, `60` Min.) und direktem Start-CTA.
   - **App-Blockierung (`#app-screen-select`)**: Dunkler Session-Zusammenfassungsblock, kategorisierte App-Auswahl und direkter Start in den Fokus-Timer.
   - **Verlauf (`#app-screen-history`)**: Dunkler Aubergine Statistik-Header (`#historyOverviewCard`), Filter-Tabs (`Alle`, `Abgeschlossen`, `Abgebrochen`) und strukturierte Session-Karten.
   - **Missionen verwalten (`#app-screen-missions`)**: Dunkler Aubergine Missions-Überblick (`#missionOverviewCardV173`), aktive Missionen, abgeschlossene Missionen und Archiv mit Wiederherstellen-, Umbenennen-, Primär- und Lösch-Aktionen.
   - **Einstellungen (`#app-screen-settings`) & Daten/Backup (`#app-screen-data`)**: Gruppierte Einstellungs-Cards für Fokus & Sitzung, Missionen & Verlauf, Sprache (`DE` / `EN`), Daten & Backup sowie App-Information (`v17.3 Home System`).

4. **Unveränderter Cosmic Timer (`#app-screen-timer`)**
   - Die dunkle kosmische Fokus-Session-Umgebung (`styles/timer.css`, `ultraCosmicV91`) bleibt für maximale Immersion während laufender Fokus-Sessions unverändert erhalten.

---

## Projektstruktur

- `index.html` — Haupt-App-Shell & Screens
- `styles/` — Stylesheets (`app.css`, `navigation.css`, `timer.css`, `tailwind.generated.css`)
- `src/` — Modulare JavaScript-Architektur (`config/`, `core/`, `data/`, `domain/`, `features/`, `infra/`, `state/`, `ui/`, `utils/`)
- `service-worker.js` & `manifest.webmanifest` — Offline-fähige PWA-Konfiguration
- `docs/` — Projektdokumentation

## Lokaler Start

Da das Projekt reines HTML, CSS und Vanilla JavaScript ohne Build-System verwendet, kann es direkt über jeden statischen Webserver gestartet werden:

```bash
npx serve . -l 3000
# oder
python3 -m http.server 3000
```
