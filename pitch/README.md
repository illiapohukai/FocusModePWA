# Focus Mode+ — Five-Minute Pitch Experience (`pitch/`)

Standalone, deterministic, offline-capable HTML presentation built from the Focus Mode+ PWA visual system (**From Plan to Orbit — Gravitational Editorial Topography**).

- **Isolated Architecture:** No runtime dependency on the PWA, Android, Cloudflare Worker, or external CDNs.
- **Target Duration:** `4:40–4:50` (`4:45` target markers across 7 scenes).
- **Primary Recording Viewport:** `1920 × 1080` (with responsive support for `1600 × 900`, `1440 × 900`, `1366 × 768`, and mobile review).
- **Camera-Safe Reservation:** Top-right `320 × 220 px` area reserved for the Loom / Google Meet presenter camera bubble.

---

## 1. Local Static Server & Playback URLs

Start a local static server from the repository root (or inside `pitch/`):

```bash
python3 -m http.server 8788 --bind 127.0.0.1
```

### Primary URLs (when served from repository root):

- **Recording Mode (Loom / Google Meet / OBS):**
  `http://127.0.0.1:8788/pitch/?mode=recording`
  - Hides all controls, debug counters, speaker notes, rehearsal timers, and placeholder warnings.
  - Automatically hides the mouse cursor after `1.8s` of inactivity while preserving pointer interaction when moved.
  - Never shows raw `[PLACEHOLDER]` bracket tokens.

- **Rehearsal Mode (Presenter HUD & Speaker Notes):**
  `http://127.0.0.1:8788/pitch/?mode=rehearsal`
  - Displays the floating presenter HUD (elapsed time, scene target time, time difference delta, current scene/beat, German speaker notes, limitation reminders, placeholder status, and top-right `320 × 220` camera-safe zone dashed guide).

- **Reduced-Motion Mode:**
  Automatically honors OS/browser `prefers-reduced-motion: reduce`, or can be previewed explicitly via:
  `http://127.0.0.1:8788/pitch/?mode=recording&reducedMotion=1`

- **Direct Scene / Beat Deep Link (for Review & Testing):**
  `http://127.0.0.1:8788/pitch/?mode=recording&scene=4&beat=5`

---

## 2. Keyboard Controls

| Key | Action |
|---|---|
| `Space` | Advance to next beat (or first beat of next scene) |
| `Shift + Space` | Step back to previous beat |
| `Right Arrow (→)` | Jump to next primary scene (Beat 1) |
| `Left Arrow (←)` | Jump to previous primary scene (Beat 1) |
| `R` | Restart current scene at Beat 1 |
| `F` | Enter or exit browser fullscreen |
| `P` | Pause or resume active Canvas animations & rehearsal clock |
| `T` | Toggle rehearsal timer panel (disabled in `recording` mode) |
| `N` | Toggle German speaker notes (disabled in `recording` mode) |
| `H` | Show or hide top navigation controls bar (disabled in `recording` mode) |
| `E` | Open or close the quantitative Evidence & Source Registry Appendix (disabled in `recording` mode) |
| `Escape` | Close open overlays or exit fullscreen |

*Note:* A `220 ms` input lock prevents accidental double-advancement during recording. Scenes never auto-advance on a timer.

---

## 3. Seven-Scene Structure & Target Timing (`4:45` Total)

| Scene | Title | Beats | Target Window | Cumulative Marker |
|---|---|---|---|---|
| **Scene 1** | **Hook** — *Ein Plan ist noch kein Fortschritt.* | 3 Beats | `30–35s` | `0:35` |
| **Scene 2** | **Problem** — *44 % Ablenkung (JIM 2025) & 4 Werkzeug-Inseln* | 2 Beats | `25–30s` | `1:05` |
| **Scene 3** | **Warum jetzt & These** — *46,5 % / 81 % KI (CHE 9/2026) & Orbit-Synthese* | 2 Beats | `25s` | `1:30` |
| **Scene 4** | **Produkt-Demonstration** — *Mission → Operator → Route → 25:00 → Fokus-Schutz → Quick Focus → Cosmic Core → Ergebnis* | 8 Beats | `65–75s` | `2:40` |
| **Scene 5** | **Fortschritt & Validierung** — *Schulprojekt → PWA → AI Missions → Android Foundation → Pilot (GEBAUT vs. NOCH ZU BEWEISEN)* | 2 Beats | `45–50s` | `3:30` |
| **Scene 6** | **Markt & Geschäftsmodell** — *2,9 Mio. Kontext → Studierende 18+ Android · Vergleich · Free/Pro Preishypothesen* | 2 Beats | `35–45s` | `4:10` |
| **Scene 7** | **Pilot & Finaler CTA** — *20 Android-Testnutzer · 14 Tage · Messroute · QR-Portal* | 2 Beats | `35–45s` | `4:45` |

---

## 4. Exact QR Code Replacement Instructions

Scene 7 displays the exact-size `240 × 240 px` (`1:1` aspect ratio) non-scannable placeholder asset at:
`pitch/assets/pilot-qr-placeholder.svg`

To replace the placeholder with the final production pilot QR code **without changing HTML, CSS, JavaScript, layout, animation, or timing**:

### Option A — Drop-in file replacement (Zero file edits)
Overwrite `pitch/assets/pilot-qr-placeholder.svg` directly with your final `1:1` SVG QR file.

### Option B — Point `qrAsset` to `pilot-qr.svg`
1. Save your final SVG QR code at `pitch/assets/pilot-qr.svg` (`viewBox="0 0 240 240"` or any `1:1` square SVG/PNG).
2. In `pitch/data/pitch-content.js`, update the single field:
   ```js
   qrAsset: "./assets/pilot-qr.svg"
   ```

---

## 5. Central Content & Validation Token Replacement

All statistics, source strings, business-model lists, pilot targets, and validation placeholders live in one central file:
`pitch/data/pitch-content.js`

- **Validation Tokens:**
  - `interviewCount: "[INTERVIEW_COUNT]"`
  - `pilotSignups: "[PILOT_SIGNUPS]"`
  - `validationQuote: "[KEY_VALIDATION_QUOTE]"`
- While these fields remain bracketed tokens, **Scene 5 automatically collapses them** and renders the approved neutral copy (`Pilot vorbereitet` · `Messbare Hypothesen definiert`).
- As soon as you replace any of those strings in `pitch/data/pitch-content.js` with real values (e.g. `interviewCount: "12"`), Scene 5 automatically switches to displaying the live metric cards.

---

## 6. GitHub Pages Deployment Instructions

Because all asset, font, script, and stylesheet paths inside `pitch/` are strictly relative (`./…`), the `pitch/` folder works unmodified under any GitHub Pages subpath:

- **Subpath deployment alongside the repository:**
  `https://<user>.github.io/FocusModePWA/pitch/?mode=recording`
  `https://<user>.github.io/FocusModePWA/pitch/?mode=rehearsal`
- **Safety Note:** The `pitch/` directory is completely isolated from `index.html` and `service-worker.js` (`CORE` precache list is untouched), so deploying or serving the repository does not alter production PWA caching, routes, or storage.

---

## 7. Offline Recording Checklist & Troubleshooting

1. Open `http://127.0.0.1:8788/pitch/?mode=rehearsal` first and press `Space` through all 7 scenes (21 total beats) to verify your Loom/Meet camera bubble sits inside the top-right `320 × 220` dashed guide.
2. Switch to `http://127.0.0.1:8788/pitch/?mode=recording` and press `F` for fullscreen.
3. Wait 2 seconds for the cursor to auto-hide, start your screen-and-camera recording, and advance beats at your natural speaking pace using `Space`.
4. **Offline Playback:** All fonts (`Inter` variable WOFF2), icons (inline SVG), operator sprite sheets, and Canvas shaders are 100% local. Once the local static server is running, no internet connection is used or required.
