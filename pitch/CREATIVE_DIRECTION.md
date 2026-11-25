# Creative Direction — Focus Mode+ Independent Argon Pitch Experience

**Model:** Gemini 4 Argon  
**Concept Title:** *From Plan to Orbit — Gravitational Editorial Topography*

---

## 1. Private Visual Exploration & Candidate Evaluation

Before building the production runtime, three materially distinct scene-system architectures were evaluated against eight criteria: product clarity, 1080p/768p recording readability, originality, five-minute pacing (4:40–4:50 target), Focus Mode+ PWA identity fidelity, deterministic reliability, laptop performance (60 fps target), and seriousness for a German EdTech startup jury.

### Candidate A — Pure Orbital Editorial (Rejected as Standalone)
- **Concept:** Swiss/German editorial grid on warm cream (`#F6F1E7`), oversized display statements, generous negative space, and magazine-like static product cutouts framed by subtle SVG orbit arcs.
- **Why Rejected Alone:** While it guarantees instant typographic authority and keeps the top-right 320×220 camera bubble zone completely clear, a purely editorial composition treats UI components as static illustrations rather than living matter that bridges the gap between planning (`PLANUNG`) and execution (`UMSETZUNG`).

### Candidate B — Pure 3D Mission Topography (Rejected as Standalone)
- **Concept:** Continuous Z-axis camera travel along a three-dimensional CSS/WebGL spline where every scene is a physical station along a spatial tunnel.
- **Why Rejected Alone:** Continuous 3D perspective camera flight risks feeling like a game trailer rather than a serious EdTech startup pitch, introduces sub-pixel text resampling during Z-axis movement at 1366×768 and 1920×1080 recording resolutions, and makes beat-by-beat presenter pacing less forgiving during live Loom recording.

### Candidate C — Pure Gravity & Particle Simulation (Rejected as Standalone)
- **Concept:** Every product artifact (tasks, calendar nodes, timer rings) behaves as an autonomous physics body pulled into a central gravitational field.
- **Why Rejected Alone:** Free-running n-body physics can obscure the concrete utility of the Focus Mode+ product workflow and complicate instant deterministic beat resets (`R`, `Left/Right`, `Space` / `Shift+Space`).

---

## 2. Chosen Direction: Gravitational Editorial Topography (A + B + C Synthesis)

We synthesized the strongest properties of all three explorations into one cohesive visual system: **Gravitational Editorial Topography**.

1. **Editorial Typographic Spine (from Candidate A):**
   - Razor-sharp left- and lower-anchored German display typography in self-hosted variable `Inter` (`font-weight: 850–950`, tight negative tracking `-0.05em`), high-contrast warm cream (`#F6F1E7`) vs. graphite (`#1D1A17`) and deep aubergine (`#25162B`), and permanently visible, 1080p-legible source citations outside the top-right 320×220 Loom camera safe zone.
2. **Spatial Trajectory Stage & Deconstructed DOM Replicas (from Candidate B):**
   - Product components (`Mission Card`, `Zeitplan Trajectory`, `Mission Route`, `Quick Focus Dial`, `Fokus-Schutz App Chips`, `Session Result Node`) are built as presentation-native DOM layers that exist freely in the 16:9 stage. They travel along choreographed orbital SVG paths, briefly dock into a translucent ghost phone frame only to establish mobile product reality in Scene 4, and immediately detach into full-stage spatial routes.
3. **Closed-Form Gravitational Matter & Cosmic Core Engine (from Candidate C):**
   - Modeled directly on the PWA's v17.9 `cosmic-core.js`, a deterministic closed-form Canvas renderer drives the Cosmic Core and orbital particle streams as a pure mathematical function of a single clock (`t`). Pausing (`P`), restarting (`R`), or jumping to any beat is 100% deterministic with zero frame accumulation drift.

---

## 3. Why This System Fits Focus Mode+

Focus Mode+ is built on a single foundational insight: **`Ein Plan ist noch kein Fortschritt.`**  
Students today have no shortage of plans (46.5% use AI daily in their studies), yet execution fractures across disconnected tools (AI chat, calendar, to-do list, timer) and regular smartphone distraction (44% during homework, JIM 2025).

By treating planning fragments as **dispersed matter** on a warm cream surface (`#F6F1E7`) and execution as **gravitational condensation** into the dark Cosmic Core (`#050714`), the visual architecture directly embodies the product thesis:
`Focus Mode+ verbindet Planung und Umsetzung.`

---

## 4. The Three Strongest Original Visual Moments

1. **Tectonic Plan Fracture → Gravitational Re-Assembly (Scene 1 & Scene 3):**
   - In Scene 1, an impressive 18-card AI study plan fractures along a physical rift between `PLANUNG` and `UMSETZUNG` as an asynchronous calendar tile fails to dock and a smartphone distraction vector slices across the trajectory, leaving the silent pixel astronaut at the edge of the gap.
   - In Scene 3, the four disconnected functional UI fragments (AI plan card, calendar deadline, to-do row, timer ring) are physically pulled along curved orbital vectors into a single connected 5-node workflow (`Ziel → Mission → Fokus → Feedback → Anpassung`), igniting a nascent Focus Core.

2. **Quick Focus Orbit → Concentrated Matter → Cosmic Core Birth (Scene 4, Beat 6 → Beat 7):**
   - Starting on the warm cream Quick Focus dial with `25:00` anchored at the exact optical center: orbit angular velocity surges, 36 orbital dust particles detach and spiral inward along logarithmic curves, the light duration track dissolves, and matter condenses behind the unmoving `25:00` digits (which cross-ink from graphite `#1D1A17` to cosmic white `#FFFFFF` without shifting position). A radial violet dusk wave transforms the entire viewport into deep space (`#050714`) as the compact dark-violet seed expands into the per-pixel 3D-shaded Cosmic Core with front-and-rear occluded elliptical orbit ribbons.

3. **Cosmic Core Contraction into Mission Progress Node & Final Pilot Portal (Scene 4 Beat 8 & Scene 7):**
   - Upon session completion (`Abgeschlossen`), the 3D Cosmic Core contracts into a glowing progress node that physically docks into Step 2 of the Mission Route, filling the trajectory line and illuminating Step 3 (`Übungsaufgaben lösen`) as the next active step (`Ergebnis fließt in den weiteren Plan ein`).
   - In Scene 7, the Cosmic Core parts its orbital rings in deep space to form a framing portal around the exact-size `[QR_PLACEHOLDER]` at the terminus of the 14-day Android pilot measurement route (`20 Android-Testnutzer · 14 Tage`).

---

## 5. Main Technical & Performance Trade-Offs

- **Why Three.js / WebGL and GSAP Were Rejected:**
  - Pulling in Three.js for a single spherical planet adds ~600 KB of JS payload, WebGL context initialization overhead, and shader compilation stutter during screen recording. Instead, we adapted the PWA's own procedural 2D Canvas per-pixel spherical FBM nebula & fresnel shading with 3D weak-perspective orbit projection (`CAMERA = 5.6` core radii, front/rear Z-split occlusion, and timer readability zone masking).
  - Instead of external GSAP dependencies, we built a zero-dependency deterministic beat timeline engine using CSS custom properties, hardware-accelerated `transform`/`opacity` transitions, and a single active-scene `requestAnimationFrame` loop.
- **Performance Guardrails:**
  - **Single Active Loop:** Only the currently active scene (and only when Canvas particles or the Cosmic Core/Operator sprite sheet are visible) runs a `requestAnimationFrame` callback. Hidden scenes have zero running timers or loops.
  - **Pre-Baked Offscreen Sprites:** Core sphere shading, atmospheric halo, nebula clouds, and particle dot palettes are baked once per layout into offscreen canvases, keeping per-frame draw calls under 2.5 ms at 1920×1080 (60 fps on integrated laptop GPUs).
  - **Reduced-Motion Path (`prefers-reduced-motion: reduce`):** Replaces orbital particle streams, logarithmic collapses, and spatial travel with calm opacity fades and static pre-rendered core states at `STATIC_CLOCK = 7.4s` while preserving every beat, claim, and product component.
