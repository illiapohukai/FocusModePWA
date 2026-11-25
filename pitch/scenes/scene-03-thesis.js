/**
 * Scene 3 — Why Now & Product Thesis: "Focus Mode+ verbindet Planung und Umsetzung."
 * 2 manually controlled beats:
 *   Beat 0: Why Now — 46,5 % daily / 81 % weekly AI usage (CHE DatenCHECK 9/2026) + scattered fragments on periphery
 *   Beat 1: Gravitational convergence — fragments move inward along orbital arcs to form
 *           Ziel → Mission → Fokus → Feedback → Anpassung around a forming Focus Core
 */

import { renderIcon, renderPixelAstronaut, renderSourceLine } from "../components/product-replicas.js";

export function createScene03Thesis(data) {
  return {
    id: "scene-03-thesis",
    title: "3 · Warum jetzt & These",
    beatCount: 2,
    theme: "cream",
    render() {
      return `
        <section class="pitch-scene scene-03" id="scene-03-thesis" data-beat="0" aria-label="Szene 3: Warum jetzt und Produktthese">
          <div class="s3-stage">
            <header class="s3-header">
              <div class="fm-eyebrow">
                <span class="fm-eyebrow-dot"></span>
                <span>WARUM JETZT &amp; PRODUKTTHESE</span>
              </div>
              <h2 class="s3-positioning-headline" id="s3PositioningHeadline">
                Focus Mode+ verbindet Planung und Umsetzung.
              </h2>
            </header>

            <div class="s3-main-grid">
              <!-- Left Side: Approved AI-Adoption Evidence (Clean number display, no robot/brain clichés) -->
              <article class="s3-evidence-panel">
                <div class="s3-ev-top">
                  <span class="s3-ev-kicker">KI-ADOPTION IM STUDIUM</span>
                  <span class="s3-ev-tag">Planung ist Alltag</span>
                </div>

                <div class="s3-primary-stat">
                  <strong class="s3-big-num">${data.aiDailyStatistic}</strong>
                  <p class="s3-big-label">${data.aiDailyLabel}</p>
                </div>

                <div class="s3-secondary-stat">
                  <div class="s3-sec-bar">
                    <i style="width: 81%"></i>
                    <span class="s3-sec-daily-marker" style="left: 46.5%" title="46,5 % täglich"></span>
                  </div>
                  <div class="s3-sec-row">
                    <strong>${data.aiWeeklyStatistic}</strong>
                    <span>${data.aiWeeklyLabel}</span>
                  </div>
                </div>

                <div class="s3-ev-limitation">
                  <span>Schwerpunkt der Erhebung: Wirtschafts-, Rechts- und verwandte Studiengänge.</span>
                </div>
              </article>

              <!-- Right Side: Gravitational Synthesis Stage (Fragments → Orbit Loop + Compact Core) -->
              <div class="s3-synthesis-stage" id="s3SynthesisStage">
                <!-- Orbital SVG Vector Field -->
                <svg class="s3-orbit-svg" viewBox="0 0 680 430" fill="none" aria-hidden="true">
                  <defs>
                    <linearGradient id="s3OrbitGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stop-color="#8B5CF6" stop-opacity="0.85"/>
                      <stop offset="50%" stop-color="#6D28D9" stop-opacity="0.95"/>
                      <stop offset="100%" stop-color="#C4B5FD" stop-opacity="0.75"/>
                    </linearGradient>
                    <radialGradient id="s3CoreGlow" cx="50%" cy="50%" r="50%">
                      <stop offset="0%" stop-color="#6D28D9" stop-opacity="0.42"/>
                      <stop offset="55%" stop-color="#4C1D95" stop-opacity="0.14"/>
                      <stop offset="100%" stop-color="#6D28D9" stop-opacity="0"/>
                    </radialGradient>
                  </defs>

                  <!-- Outer gravitational pull trajectories -->
                  <ellipse cx="340" cy="215" rx="268" ry="155" stroke="#D5CBBF" stroke-width="1.5" stroke-dasharray="5 7" class="s3-outer-pull-ring"/>
                  <ellipse cx="340" cy="215" rx="205" ry="118" stroke="url(#s3OrbitGrad)" stroke-width="2.6" class="s3-connected-route-ring"/>
                  <ellipse cx="340" cy="215" rx="145" ry="76" stroke="#A78BFA" stroke-opacity="0.38" stroke-width="1.4" transform="rotate(-11 340 215)" class="s3-inner-tilt-ring"/>
                  <circle cx="340" cy="215" r="96" fill="url(#s3CoreGlow)" class="s3-core-aura"/>
                </svg>

                <!-- 4 Transforming Fragments that converge into the 5 Workflow Waypoints -->
                <div class="s3-morph-fragments" aria-hidden="true">
                  <div class="s3-mfrag s3-mfrag-1">
                    <span class="s3-mfrag-old">KI-Plan</span>
                    <span class="s3-mfrag-arrow">→</span>
                    <span class="s3-mfrag-new">Mission</span>
                  </div>
                  <div class="s3-mfrag s3-mfrag-2">
                    <span class="s3-mfrag-old">Kalender-Frist</span>
                    <span class="s3-mfrag-arrow">→</span>
                    <span class="s3-mfrag-new">Wegpunkt</span>
                  </div>
                  <div class="s3-mfrag s3-mfrag-3">
                    <span class="s3-mfrag-old">To-do</span>
                    <span class="s3-mfrag-arrow">→</span>
                    <span class="s3-mfrag-new">Nächster Schritt</span>
                  </div>
                  <div class="s3-mfrag s3-mfrag-4">
                    <span class="s3-mfrag-old">Timer-Ring</span>
                    <span class="s3-mfrag-arrow">→</span>
                    <span class="s3-mfrag-new">Fokus-Orbit</span>
                  </div>
                </div>

                <!-- Forming Compact Focus Core at the Center -->
                <div class="s3-nascent-core" id="s3NascentCore">
                  <div class="s3-core-sphere">
                    <div class="s3-core-rim"></div>
                    <span class="s3-core-label">FOCUS CORE</span>
                    <strong class="s3-core-sub">Geschützte Umsetzung</strong>
                  </div>
                </div>

                <!-- Connected 5-Step Workflow Loop: Ziel → Mission → Fokus → Feedback → Anpassung -->
                <div class="s3-workflow-nodes" aria-label="Focus Mode+ Workflow: Ziel, Mission, Fokus, Feedback, Anpassung">
                  <div class="s3-wf-node s3-wf-1">
                    <span class="s3-wf-step">01</span>
                    <strong>Ziel</strong>
                    <small>Prüfung &amp; Frist</small>
                  </div>
                  <div class="s3-wf-node s3-wf-2">
                    <span class="s3-wf-step">02</span>
                    <strong>Mission</strong>
                    <small>Strukturierte Route</small>
                  </div>
                  <div class="s3-wf-node s3-wf-3 is-primary">
                    <span class="s3-wf-step">03</span>
                    <strong>Fokus</strong>
                    <small>Ein nächster Schritt</small>
                  </div>
                  <div class="s3-wf-node s3-wf-4">
                    <span class="s3-wf-step">04</span>
                    <strong>Feedback</strong>
                    <small>Ergebnis erfassen</small>
                  </div>
                  <div class="s3-wf-node s3-wf-5">
                    <span class="s3-wf-step">05</span>
                    <strong>Anpassung</strong>
                    <small>Route aktualisieren</small>
                  </div>
                </div>

                <!-- Supporting Pixel Astronaut drawn toward the forming orbit -->
                <div class="s3-astronaut-anchor">
                  ${renderPixelAstronaut({
                    src: data.astronautAsset,
                    id: "s3Astronaut",
                    variant: "orbit-entry"
                  })}
                </div>
              </div>
            </div>

            <!-- Linear Flow Ribbon + Source Bar -->
            <footer class="s3-footer">
              <div class="s3-flow-chain" aria-label="Durchgängiger Workflow">
                <span class="s3-chain-pill">Ziel</span>
                <span class="s3-chain-arrow">→</span>
                <span class="s3-chain-pill">Mission</span>
                <span class="s3-chain-arrow">→</span>
                <span class="s3-chain-pill is-highlight">Fokus</span>
                <span class="s3-chain-arrow">→</span>
                <span class="s3-chain-pill">Feedback</span>
                <span class="s3-chain-arrow">→</span>
                <span class="s3-chain-pill">Anpassung</span>
              </div>
              ${renderSourceLine({
                source: data.aiStatisticSource,
                note: "Befragung schwerpunktmäßig Wirtschafts-, Rechts- und verwandte Fächer"
              })}
            </footer>
          </div>
        </section>
      `;
    },
    onBeat(el, beatIndex) {
      el.dataset.beat = String(beatIndex);
    }
  };
}
