# Focus Mode+ · Five-Minute Pitch Experience

A standalone, deterministic, cinematic HTML presentation for the recorded five-minute pitch
(German). Concept: **From Plan to Orbit**. Seven scenes, manually advanced beats, product-native
replicas of the PWA, the Quick Focus → Cosmic Core transformation, recording and rehearsal modes.

It is isolated from the app: no PWA runtime, storage, service worker, Worker, AI or Android code is
used or changed. Every asset is local; playback makes no network request.

---

## 1. Run it

Serve the `pitch/` folder **on its own origin** (recommended for recording):

```bash
cd pitch
python3 -m http.server 4173
```

| Purpose | URL |
|---|---|
| Recording (Loom, Meet, OBS) | http://localhost:4173/?mode=recording |
| Rehearsal (timer, cues, notes, warnings) | http://localhost:4173/?mode=rehearsal |
| Review (clean stage, overlays on demand) | http://localhost:4173/ |
| Jump to a state | `?scene=4&beat=5` (scene 1–7, beat from 0) |
| Force reduced motion | `?motion=reduced` |
| Show the camera-safe outline | `?safe=show` (always shown in rehearsal) |

Why its own origin: the PWA service worker (scope `./` of the app) would cache pitch files
cache-first if the pitch ran under the app's origin in a browser that has the app installed.
Serving `pitch/` directly on port 4173 avoids that entirely.

Opening `index.html` via `file://` works as an emergency fallback, but Chrome blocks local fonts
there (CORS), so Inter falls back to a system font. Use the local server for recording.

## 2. Controls

| Key | Action |
|---|---|
| Space | next beat; after the last beat, next scene |
| Shift + Space | previous beat (settled, no animation) |
| → / ← (also PageDown / PageUp) | next scene / previous scene (its settled final state) |
| R | restart the current scene |
| F | fullscreen on/off |
| P | pause / resume every animation (timeline, canvas, CSS) |
| T | rehearsal timer overlay (Shift + T resets the timer) |
| N | speaker notes |
| H | controls help |
| E | evidence appendix (sources and limitations; never part of the recording) |
| Esc | close overlays, then leave fullscreen |

Auto-repeat is ignored and a short input lock (420 ms per beat, 700 ms per scene) prevents
accidental double advancement. If you press Space while a beat's sub-sequence is still running,
that beat settles instantly and the next one starts cleanly. In recording mode T, N, H and E do
nothing, so nothing can be revealed by accident. On touch devices (review only), tap the right
60 % for the next beat and the left 40 % for the previous one.

## 3. The seven scenes

| # | Scene | Beats (Space) | Target end | Content |
|---|---|---|---|---|
| 1 | Hook | standby → plan → drift → reveal | 0:35 | 18-step AI plan assembles, separates out of sequence, a calendar fragment fails to connect, a notification crosses the route, the route breaks. „Ein Plan ist noch kein Fortschritt." PLANUNG \| UMSETZUNG, astronaut at the gap. |
| 2 | Problem | stat → fragments | 1:05 | 44 % (JIM-Studie 2025), ten learner markers (4 full + the fifth 40 % filled) hit by a phone-shaped interruption; four disconnected tool fragments. |
| 3 | Why now · thesis | stat → converge → thesis | 1:30 | 46,5 % / 81 % (CHE DatenCHECK 9/2026); the fragments become Mission, goal waypoint and next step, the timer ring becomes the orbit, a compact Core forms. „Focus Mode+ verbindet Planung und Umsetzung." |
| 4 | Product demo | create → generate → route → next → protect → launch → active → result | 2:40 | See section 4. |
| 5 | Progress · validation | route → built → prove | 3:30 | Schulprojekt → PWA → AI Missions → Android Foundation → Pilot (outlined); GEBAUT vs. NOCH ZU BEWEISEN; validation values or neutral copy. |
| 6 | Market · model | segment → compare → model | 4:10 | 2,9 Mio. (Destatis) as broad context only, first target group; separated category domains vs. the connected workflow; Free / Pro with three labelled price hypotheses. |
| 7 | Pilot · CTA | pilot → measure → close | 4:45 | Core as portal around the QR slot; 20 Android-Testnutzer, 14 Tage; measurement route; internal targets; „Nicht die nächsten Features. Die ersten echten Nutzer." |

Scene 1 loads in **standby** (cream stage, wordmark, astronaut): start the recording, introduce
yourself, then press Space. Total target 4:40–4:50 (rehearsal markers 0:35, 1:05, 1:30, 2:40,
3:30, 4:10, 4:45). Scenes never advance automatically.

## 4. Product demo (scene 4)

1. **create** – goal „Mathe-Klausur vorbereiten", deadline „Do, 27. November" and a worksheet float
   in separately, then assemble into the Mission creation screen inside a temporary phone outline.
2. **generate** – the approved pixel operator works at the terminal (PWA frames); the planet success
   plays once. Copy is the PWA's own („Deine Mission entsteht" → „Deine Mission ist bereit").
3. **route** – the phone dissolves; the four generation dots fly out and become the Mission route
   (Grundlagen prüfen ✓ · Lineare Funktionen verstehen · Übungsaufgaben lösen · Probeklausur
   bearbeiten · Klausur flag). The next step is highlighted.
4. **next** – the route recedes; „Lineare Funktionen üben · 25:00" comes forward.
5. **protect** – generic app categories are selected („Was lenkt dich ab?", „Geschützter Fokus ·
   vorbereitet"), with the honest note that the protective effect is tested in the pilot.
6. **launch** – Quick Focus setup (PWA dial geometry), then the transformation (≈ 5 s, auto):
   orbit charge → matter spirals inward → the light track dissolves → a compact dark sphere forms
   under the digits → the cream stage darkens outward from the centre → the sphere grows into the
   Cosmic Core → orbits, dust and stars form. The timer digits never move; they are real HTML.
7. **active** – „Fokus-Sitzung · Lineare Funktionen üben", „Fokus-Schutz aktiv", live countdown.
8. **result** – „Abgeschlossen"; the Core contracts into the step-2 node, the step completes, step 3
   becomes „Nächster Schritt", the Mission advances to „2 von 4", a line returns to the plan:
   „Ergebnis fließt in den weiteren Plan ein."

All demo data are fixtures inside the scene file. No API, no AI, no storage, no network.

## 5. Content configuration (single file)

`data/pitch-content.js` holds every research, validation, business-model and QR value. Scenes read
only from it.

- Research: `problemStatistic*`, `aiDaily*`, `aiWeekly*`, `aiStatistic*`, `addressableSegment*`.
- Validation: `interviewCount`, `pilotSignups`, `validationQuote`, `validationQuoteAuthor`.
- Model: `freeDefinition`, `proDefinition`, `priceHypotheses`, `priceHypothesesUnit`.
- Pilot: `pilotSize`, `pilotDuration`, `pilotRoute`, `activationTarget`, `threeSessionTarget`,
  `daySevenTarget`, `followUpInterviews`.
- Progress: `progressRoute`, `built.pwa`, `built.android`, `buildFact`, `toProve`.
- QR: `qrAsset`; camera area: `cameraSafeArea`.

**Placeholders.** A value written as `[TOKEN]` is unresolved and never rendered in any mode. While
`interviewCount`, `pilotSignups` and `validationQuote` are unresolved, scene 5 shows the neutral
copy „Pilot vorbereitet" and „Messbare Hypothesen definiert". Replace a token with a real value
(only values that were actually collected) and the strip shows it instead; the layout collapses
cleanly for any combination. Rehearsal mode lists every unresolved token.

## 6. QR code (one file)

The stage always shows **`pitch/assets/pilot-qr.svg`** in a fixed 300 × 300 px slot (on the
1920 × 1080 stage) on a cream plate. The repository ships `pilot-qr.svg` as a copy of
`pilot-qr-placeholder.svg`: a non-scannable placeholder („QR · Pilot-Link folgt") that encodes
nothing.

To go live:

1. Create the final QR as a **square SVG with its own quiet zone** (dark modules on white),
   pointing to the final pilot link.
2. Overwrite `pitch/assets/pilot-qr.svg` with it. Keep the file name.
3. Reload. Nothing else changes (HTML, CSS, JS, layout, motion and timing stay the same).
4. Rehearsal mode reports „eigener QR eingesetzt – vor der Aufnahme scannen". Scan it from the
   screen before recording.

A PNG works too: save it as `pitch/assets/pilot-qr.png` and change the single `qrAsset` line in
`data/pitch-content.js`. `pilot-qr-placeholder.svg` stays as the backup.

## 7. Speaker notes and sources

- `data/speaker-notes.js` – concise German notes per beat: spoken line, key sentence, limitation,
  source reminder, pronunciation and the scene's target end time (≈ 520 spoken words; per scene: 60 · 48 · 56 · 132 · 85 · 76 · 60).
- `data/sources.js` – source registry with population, sample, meaning and limitations of each
  approved figure. Compact source lines sit next to each claim on stage; the full registry is the
  evidence appendix (key E, rehearsal and review only).

Approved evidence used: JIM-Studie 2025 (44 %, 12–19 Jahre, n = 1.200, Selbstauskunft);
CHE DatenCHECK 9/2026 (46,5 % daily, 81 % at least weekly, 28.584 gültige Angaben, mainly economics,
law and related fields); Destatis Wintersemester 2024/25 (2,9 Mio. Studierende, broad segment
only). No revenue projection, MRR scenario or conversion assumption is used anywhere.

## 8. Recording checklist

1. Close other apps, turn on Do Not Disturb, hide the bookmarks bar.
2. Start the server (section 1). Load `?mode=rehearsal` once and run through; check the QR status.
3. Open `http://localhost:4173/?mode=recording` in Chrome or Edge at 100 % zoom; press **F**.
4. Screen 1920 × 1080 (1366 × 768 also works; the stage scales and letterboxes in the scene's
   background colour).
5. Camera bubble: **top right**. The stage keeps a 320 × 220 px area there free of headlines,
   statistics, sources, the QR code, demo actions and the CTA. Size the bubble to fit that corner.
6. The cursor hides after 1.5 s without movement. Press Space to start after your introduction.

**Offline:** once the page has loaded from the local server, you can disconnect from the internet;
nothing is fetched during playback.

## 9. GitHub Pages

The pitch is a plain static folder with relative paths, so it can be served from GitHub Pages as a
review/backup experience. It is not deployed by this branch.

- **Same repository:** once `pitch/` is on the branch GitHub Pages publishes, it is served at
  `https://<user>.github.io/<repository>/pitch/` (e.g. `…/pitch/?mode=recording`). This only adds
  a folder; the app at the root and its service worker are unchanged, and the pitch is not in the
  app's precache.
  *Caveat:* in a browser where the app is installed, the app's service worker (scope = site root)
  also controls `/pitch/` and runtime-caches its static files cache-first. A replaced QR may then
  show the old file until that cache is cleared. For recording, use the local server; for a
  shareable link, prefer the separate option below or open the link in a private window.
- **Separate site (no interaction with the app):** push the contents of `pitch/` to the root of a
  separate repository or a dedicated Pages branch and enable Pages there.

## 10. Review and QA

```bash
node pitch/tools/review.js          # screenshots + contact sheet + checks into pitch/review/
node pitch/tools/review.js --quick  # checks only
```

Requires Node and Playwright with Chromium. It serves `pitch/` on its own port, captures every
scene's opening, strongest and ending state at 1920 × 1080 (recording mode), ending states at
1366 × 768, every scene with reduced motion, rehearsal/recording/mobile samples, and builds
`review/contact-sheet.jpg`. Checks: console errors, failed or non-local requests, raw placeholder
tokens on stage, visible text in the camera-safe area, visible text outside the stage, JS loops or
CSS animations left running in hidden scenes, overlays in recording mode, cursor hiding, pause.

## 11. Assets and provenance

| Asset | Source |
|---|---|
| `assets/operator/orbit-standing.png` | The approved standing astronaut. Background keyed out by a flood fill from the image border (only the flat background colour), 2 × 2 area average, 1-bit alpha, palette snap. Not redrawn, not split, no new poses. Original: `assets/operator/source/orbit-standing-source.jpg`; details in `orbit-standing.json`. Rendered with `image-rendering: pixelated` at exact 1/3 or 1/2 size. Animation: whole-sprite translation (2–4 px), up to 1.5° rotation, opacity, shadow and light only. To replace it, overwrite the PNG; any size works, the CSS sets the box. |
| `assets/operator/mission-operator-*.png`, `.json` | Copied from the PWA (`assets/operator/`), frames from the approved source video. |
| `assets/fonts/inter/` | Inter variable (latin, latin-ext), copied from the PWA. SIL Open Font License (`LICENSE.txt`). |
| `components/icons.js` | Icon geometry extracted from the PWA's vendored Lucide build. ISC (`components/LICENSE-lucide.txt`). |
| `components/focus-core.js` | Pitch-only port of the PWA Cosmic Core model (`src/core/cosmic-core.js`): seeded noise, lit sphere with the timer readability hollow, atmosphere, the five asymmetric orbits with front/rear depth, dust, inner stars, deep field; plus inflow, seed, growth and contraction parameters. |

Visual tokens (`styles/tokens.css`) are copied from the PWA: cream `#F6F1E7`, surface `#FCF9F4`,
graphite `#1D1A17`, violet `#6D28D9`, aubergine `#25162B`, green `#16A34A`, the Mission identity
themes, the dial gradient and the Focus-screen space gradient.

## 12. Structure

```
pitch/
├── index.html              stage, seven scene sections, ordered classic scripts
├── pitch.js                bootstrap (waits for the local font)
├── data/                   pitch-content.js · speaker-notes.js · sources.js
├── engine/                 timeline.js (single pausable clock) · presentation-controller.js ·
│                           modes.js · rehearsal.js · keyboard.js
├── components/             dom.js · icons.js · product.js (replicas) · focus-core.js · operator.js
├── scenes/                 scene-01-hook.js … scene-07-pilot.js
├── styles/                 tokens.css · engine.css · components.css · scenes.css
├── assets/                 fonts/ · operator/ · pilot-qr.svg · pilot-qr-placeholder.svg
├── tools/review.js         deterministic review walkthrough
└── review/                 screenshots and contact sheet
```

No build step, no framework, no package.json, no CDN. Motion: one rAF timeline (play time stops
while paused), CSS transitions driven by cumulative beat classes, Canvas only for the Core.

## 13. Troubleshooting

| Symptom | Fix |
|---|---|
| Wrong font / blocky layout | You opened `file://`. Use the local server. |
| Old QR shows after replacing it | Same-origin app service worker cache (section 9): use the local server or a private window. |
| Sequence looks half-finished | You pressed Space mid-sequence: the beat settled. Press R to replay the scene. |
| Everything frozen | Animation is paused: press P (rehearsal shows „Pausiert"). |
| Overlays don't open | Recording mode disables them; use `?mode=rehearsal`. |
| Choppy Core on a weak laptop | Close other tabs, use Chrome/Edge with hardware acceleration on, record at 1920 × 1080 with 100 % zoom. |

## 14. Honest limitations

- The demo is a deterministic replica with fixtures; it shows the product flow, not a live run.
- Android app selection is implemented; the protective effect has not been validated on physical
  devices (see `android/docs/device-smoke-test.md`). The pitch says so.
- Frame timing was measured in a headless, software-rendered Chromium (≈ 60 fps for the Core,
  launch average ≈ 24 ms per frame). It has not been measured on a physical Windows laptop.
- Validation values and the real QR code are not supplied yet; the stage shows neutral copy and a
  non-scannable placeholder.
