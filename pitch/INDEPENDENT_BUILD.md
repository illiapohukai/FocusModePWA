# Independent Build Provenance — Focus Mode+ Five-Minute Pitch Experience

## 1. Build Identity & Git Provenance

- **Model:** Gemini 4 Argon
- **Starting Branch:** `claude/v18-1-android-stability-parity-rmi3l8`
- **Starting Commit (`HEAD` at checkout):** `5c772cbd300389ba6c81aa1426c15527583169fc`
- **Working Tree State Before Build:** Clean (`nothing to commit, working tree clean`); `pitch/` directory did not exist (`DOES_NOT_EXIST`).
- **New Argon Branch:** `argon/focus-mode-pitch-independent` (created directly from `5c772cbd300389ba6c81aa1426c15527583169fc`)
- **Creation Timestamp:** `2026-11-25T14:28:06-08:00` (`2026-11-25T22:28:06Z`)

---

## 2. Independence & Non-Access Attestation

1. **No Previous Pitch Implementation Accessed:**  
   No remote branches were fetched, listed, inspected, compared against, or checked out. None of `git fetch`, `git pull`, `git branch -r`, `git branch --all`, `git log --all`, or remote GitHub branch/code searches were executed. No previous `pitch/` directory, commit `ee01ad6`, `claude/focus-mode-pitch-experience`, `claude/pitch-deck-html`, or any prior agent output was accessed or referenced.
2. **Independent Creation from Attached PWA Baseline:**  
   This presentation was designed and implemented 100% independently from the attached PWA baseline branch (`claude/v18-1-android-stability-parity-rmi3l8` at `5c772cbd300389ba6c81aa1426c15527583169fc`), the attached approved standing pixel astronaut image (`/home/antigravity/381001880_1795020750048358.jpeg`), and the authoritative product specification.

---

## 3. PWA Source Files Inspected for Visual & Product Reference

Only files in the currently attached working tree were inspected:

- `CLAUDE.md` — Architectural rules, visual system constraints, and testing practices.
- `CHANGELOG.md` — Product development milestones (`v17.3` through `v17.5.1`).
- `styles/app.css` — Self-hosted `Inter` `@font-face` declarations and base surfaces.
- `styles/navigation.css` — Authoritative warm cream (`#F6F1E7`), surface (`#FCF9F4`), deep aubergine (`#25162B`), graphite (`#1D1A17`), primary violet (`#6D28D9`), and restrained green (`#16A34A`) tokens.
- `styles/quick-focus.css` — Circular duration dial (`.qb-dial`), SVG arc gradients, orbit rings (`.qb-orbit`), and Fokus-Schutz summary row.
- `styles/timer.css` — Shared timer typography contract (`--fm-timer-size`, `--fm-timer-tracking`, `.fm-timer-digits`), Cosmic Core anchor (`.uc-scene`), and protected-focus badge (`.uc-protect`).
- `styles/missions.css` — Mission Creation (`#app-screen-goal`), Zeitplan study-planning board, and Mission Hero cards.
- `styles/motion.css` — Authoritative motion tokens (`--motion-*`, `--ease-*`) and layering rules.
- `src/core/cosmic-core.js` — v17.9 Cosmic Core procedural 2D Canvas per-pixel spherical FBM nebula shading, fresnel rim light, 3D weak-perspective elliptical orbits, and front/rear Z-split occlusion.
- `src/core/motion-controller.js` — v17.16 Quick Focus launch choreography (`animateQuickFocusLaunch`).
- `src/features/missions/mission-operator.js` & `assets/operator/mission-operator.json` — Pixel-art terminal operator work loop and one-shot planet thought-cloud success sequence.
- `src/features/missions/multi-mission-ui.js` & `src/features/missions/mission-renderer.js` — Mission Hero card view model, step structure, and progress bar behavior.
- `tests/security/run-all.js` & `tests/security/browser-security.test.js` — Local verification conventions.

---

## 4. Copied, Transformed, or Reconstructed Approved Assets

| Original Path | Pitch Path | Operation | Purpose |
|---|---|---|---|
| `/home/antigravity/381001880_1795020750048358.jpeg` | `pitch/assets/operator/orbit-standing.png` | **Converted & Exterior-Cropped (Lossless Pixel Grid Preserved):** Flood-filled only the uniform exterior rectangular background outside the black pixel border to transparent (`RGBA`) and cropped with a 4 px margin (`630 × 868`). Zero redrawing, zero proportion change, zero body-part splitting. | Silent supporting pixel astronaut in Scene 1 (Hook), Scene 3 (Orbit transition), Scene 5 (Progress route), and Scene 7 (Pilot QR portal). |
| `assets/operator/mission-operator.json` | `pitch/assets/operator/mission-operator.json` | **Copied verbatim** | Frame dimensions (`266 × 358`), FPS (`12`), and grid metadata for the terminal operator sprite sheets. |
| `assets/operator/mission-operator-work.png` | `pitch/assets/operator/mission-operator-work.png` | **Copied verbatim** | 16-frame (`4 × 4`) pixel-art operator terminal working loop for Scene 4 Demo Beat 2. |
| `assets/operator/mission-operator-success.png` | `pitch/assets/operator/mission-operator-success.png` | **Copied verbatim** | 24-frame (`6 × 4`) one-shot planet thought-cloud generation success animation for Scene 4 Demo Beat 2. |
| `assets/fonts/inter/inter-latin-wght-normal.woff2` | `pitch/assets/fonts/inter/inter-latin-wght-normal.woff2` | **Copied verbatim** | Self-hosted variable `Inter` (100–900) Latin subset for 100% offline deterministic typography. |
| `assets/fonts/inter/inter-latin-ext-wght-normal.woff2` | `pitch/assets/fonts/inter/inter-latin-ext-wght-normal.woff2` | **Copied verbatim** | Self-hosted variable `Inter` (100–900) Latin-Extended subset for German umlauts and symbols. |
| `assets/fonts/inter/LICENSE.txt` | `pitch/assets/fonts/inter/LICENSE.txt` | **Copied verbatim** | SIL Open Font License 1.1 for self-hosted `Inter`. |
| `icon-192.png` | `pitch/assets/app/icon-192.png` | **Copied verbatim** | Focus Mode+ brand emblem in top-left badge and favicon. |
| *(Presentation-Native SVG)* | `pitch/assets/pilot-qr-placeholder.svg` | **Created (New)** | Exact-size (`240 × 240`, `1:1`) non-scannable QR placeholder showing `QR` and `Pilot-Link folgt`, drop-in replaceable by `pitch/assets/pilot-qr.svg`. |

---

## 5. Complete List of Files Created Under `pitch/`

- `pitch/index.html` — Standalone semantic HTML presentation shell.
- `pitch/pitch.css` — Complete token layer, scene layouts, beat choreography, responsive rules (`1920×1080` to `1366×768`), camera-safe zone reservation, and reduced-motion rules.
- `pitch/pitch.js` — Bootstrap entrypoint resolving `?mode=recording`, `?mode=rehearsal`, and `prefers-reduced-motion`.
- `pitch/README.md` — Local server instructions, recording/rehearsal URLs, keyboard controls, QR replacement guide, and GitHub Pages documentation.
- `pitch/INDEPENDENT_BUILD.md` — This provenance and independence attestation document.
- `pitch/CREATIVE_DIRECTION.md` — Documentation of the Argon visual exploration, chosen creative synthesis, three strongest visual moments, and performance trade-offs.
- `pitch/data/pitch-content.js` — Central configuration for all statistics, sources, validation tokens, business model definitions, pilot targets, and demo fixtures.
- `pitch/data/speaker-notes.js` — Concise German speaker notes, limitation reminders, and timing markers (`0:35` to `4:45`).
- `pitch/engine/presentation-controller.js` — Scene/beat state machine, debounced keyboard navigation, cursor auto-hide in recording mode, presenter rehearsal HUD, and evidence appendix modal.
- `pitch/components/focus-core.js` — Presentation-native 3D-projected Canvas Cosmic Core renderer (with Quick Focus matter convergence and Scene 7 portal mode) and deterministic Mission Operator sprite-sheet player.
- `pitch/components/product-replicas.js` — Presentation-native DOM replicas (Quick Focus duration dial, Mission Hero card, standing pixel astronaut stage, source citation bar, inline SVG icons).
- `pitch/scenes/scene-01-hook.js` — Scene 1: Hook (*Ein Plan ist noch kein Fortschritt.*).
- `pitch/scenes/scene-02-problem.js` — Scene 2: Problem (*44 % JIM-Studie 2025 & 4 Werkzeug-Inseln*).
- `pitch/scenes/scene-03-thesis.js` — Scene 3: Why Now & Product Thesis (*46,5 % / 81 % CHE 9/2026 & Orbit-Synthese*).
- `pitch/scenes/scene-04-demo.js` — Scene 4: Product Demonstration (*8 deterministic beats including Quick Focus → Cosmic Core*).
- `pitch/scenes/scene-05-progress.js` — Scene 5: Product Progress & Validation (*GEBAUT vs. NOCH ZU BEWEISEN*).
- `pitch/scenes/scene-06-market.js` — Scene 6: Market & Business Model (*2,9 Mio. Destatis Kontext, Vergleich, Free/Pro Preishypothesen*).
- `pitch/scenes/scene-07-pilot.js` — Scene 7: Pilot & Final CTA (*20 Android-Testnutzer · 14 Tage · QR-Portal*).
- `pitch/assets/pilot-qr-placeholder.svg` — Replaceable exact-size (`240 × 240`) non-scannable QR placeholder.
- `pitch/assets/app/icon-192.png` — Copied app brand icon.
- `pitch/assets/fonts/inter/inter-latin-wght-normal.woff2` — Copied self-hosted variable font.
- `pitch/assets/fonts/inter/inter-latin-ext-wght-normal.woff2` — Copied self-hosted variable font.
- `pitch/assets/fonts/inter/LICENSE.txt` — Copied font license.
- `pitch/assets/operator/orbit-standing.png` — Transparent cropped standing pixel astronaut sprite.
- `pitch/assets/operator/mission-operator.json` — Copied operator sprite sheet metadata.
- `pitch/assets/operator/mission-operator-work.png` — Copied operator work sprite sheet.
- `pitch/assets/operator/mission-operator-success.png` — Copied operator success sprite sheet.
- `pitch/review/generate_review.py` — Automated Playwright verification & screenshot capture script.
- `pitch/review/contact-sheet.png` — 7 × 3 visual review contact sheet (21 states).
- `pitch/review/s01-01-opening.png`, `pitch/review/s01-02-strongest.png`, `pitch/review/s01-03-ending.png` — Scene 1 review screenshots.
- `pitch/review/s02-01-opening.png`, `pitch/review/s02-02-strongest.png`, `pitch/review/s02-03-ending.png` — Scene 2 review screenshots.
- `pitch/review/s03-01-opening.png`, `pitch/review/s03-02-strongest.png`, `pitch/review/s03-03-ending.png` — Scene 3 review screenshots.
- `pitch/review/s04-01-opening.png`, `pitch/review/s04-02-strongest.png`, `pitch/review/s04-03-ending.png`, `pitch/review/s04-demo-beat2-operator.png`, `pitch/review/s04-demo-beat3-route.png`, `pitch/review/s04-demo-beat4-next-step.png`, `pitch/review/s04-demo-beat5-protection.png`, `pitch/review/s04-demo-beat6-quickfocus-dial.png` — Scene 4 review screenshots.
- `pitch/review/s05-01-opening.png`, `pitch/review/s05-02-strongest.png`, `pitch/review/s05-03-ending.png` — Scene 5 review screenshots.
- `pitch/review/s06-01-opening.png`, `pitch/review/s06-02-strongest.png`, `pitch/review/s06-03-ending.png` — Scene 6 review screenshots.
- `pitch/review/s07-01-opening.png`, `pitch/review/s07-02-strongest.png`, `pitch/review/s07-03-ending.png` — Scene 7 review screenshots.
- `pitch/review/mode-rehearsal-1080p.png`, `pitch/review/viewport-1366x768-s04-core.png`, `pitch/review/viewport-1366x768-s07-pilot.png`, `pitch/review/reduced-motion-s04-core.png` — Mode, responsive, and accessibility review screenshots.
