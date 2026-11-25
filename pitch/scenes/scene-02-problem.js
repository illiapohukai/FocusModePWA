/**
 * Scene 2 — Problem: 44% Smartphone Distraction (JIM 2025) & Four Disconnected Workflow Islands
 * 2 manually controlled beats:
 *   Beat 0: 44% JIM-Studie 2025 evidence with 10 learner node markers + smartphone interruption wave
 *   Beat 1: Four disconnected functional UI fragments (AI Planning, Calendar, To-do, Focus Timer)
 */

import { renderIcon, renderSourceLine } from "../components/product-replicas.js";
import { isUnresolvedPlaceholder } from "../data/pitch-content.js";

export function createScene02Problem(data) {
  const hasStat = !isUnresolvedPlaceholder(data.problemStatistic);

  // 10 restrained learner/focus node markers (4 full + 0.4 partial = 44%)
  const markersHtml = Array.from({ length: 10 }, (_, i) => {
    const idx = i + 1;
    const stateClass = i < 4 ? "is-interrupted" : i === 4 ? "is-partial" : "is-calm";
    return `
      <div class="s2-learner-marker ${stateClass}" style="--m-idx:${i}">
        <div class="s2-marker-ring">
          <span class="s2-marker-core"></span>
        </div>
        <span class="s2-marker-num">${idx < 10 ? `0${idx}` : idx}</span>
      </div>
    `;
  }).join("");

  return {
    id: "scene-02-problem",
    title: "2 · Problem",
    beatCount: 2,
    theme: "cream",
    render() {
      return `
        <section class="pitch-scene scene-02 ${hasStat ? "has-stat" : "no-stat"}" id="scene-02-problem" data-beat="0" aria-label="Szene 2: Problem">
          <div class="s2-stage">
            <header class="s2-header">
              <div class="fm-eyebrow">
                <span class="fm-eyebrow-dot"></span>
                <span>DAS DOPPELTE PROBLEM · ABLENKUNG &amp; FRAGMENTIERUNG</span>
              </div>
              <h2 class="s2-title">Ablenkung trifft auf getrennte Werkzeuge.</h2>
            </header>

            <div class="s2-main-grid">
              <!-- Left Column: Approved JIM-Studie 2025 Statistic & 10-Marker Interruption Field -->
              ${
                hasStat
                  ? `
                <article class="s2-stat-surface" id="s2StatSurface">
                  <div class="s2-stat-top">
                    <span class="s2-stat-kicker">ALLTAGSREALITÄT BEIM LERNEN</span>
                    <span class="s2-stat-pill">Selbstauskunft · 12–19 J.</span>
                  </div>

                  <div class="s2-stat-hero">
                    <strong class="s2-stat-number">${data.problemStatistic}</strong>
                    <p class="s2-stat-label">${data.problemStatisticLabel}</p>
                  </div>

                  <!-- Product-native 10-marker representation (no pie chart) -->
                  <div class="s2-marker-visualizer" aria-label="4,4 von 10 Lernenden regelmäßig abgelenkt">
                    <div class="s2-interruption-wave" aria-hidden="true">
                      <span class="s2-wave-phone">${renderIcon("smartphone")}</span>
                      <span class="s2-wave-beam"></span>
                    </div>
                    <div class="s2-markers-row">
                      ${markersHtml}
                    </div>
                    <div class="s2-markers-legend">
                      <span class="s2-leg-item is-hit"><i></i> Regelmäßig beim Lernen unterbrochen (44 %)</span>
                      <span class="s2-leg-item is-rest"><i></i> Übrige Befragte</span>
                    </div>
                  </div>
                </article>
              `
                  : ""
              }

              <!-- Right Column: Four Disconnected Functional UI Fragments (No Logo Wall) -->
              <div class="s2-islands-field" id="s2IslandsField">
                <div class="s2-islands-header">
                  <span class="s2-islands-kicker">FRAGMENTIERTER WORKFLOW</span>
                  <span class="s2-islands-warn">4 Werkzeuge · 0 Rückkopplung</span>
                </div>

                <div class="s2-islands-canvas">
                  <!-- Broken SVG connectors between the 4 islands -->
                  <svg class="s2-broken-links" viewBox="0 0 620 400" fill="none" aria-hidden="true">
                    <path d="M 250 110 L 350 110" stroke="#B8AFA3" stroke-width="2" stroke-dasharray="6 8"/>
                    <path d="M 455 175 L 455 235" stroke="#B8AFA3" stroke-width="2" stroke-dasharray="6 8"/>
                    <path d="M 350 300 L 250 300" stroke="#B8AFA3" stroke-width="2" stroke-dasharray="6 8"/>
                    <path d="M 155 235 L 155 175" stroke="#B8AFA3" stroke-width="2" stroke-dasharray="6 8"/>
                    <!-- Disconnection X nodes -->
                    <circle cx="300" cy="110" r="11" fill="#EFE9DD" stroke="#C5BBAE" stroke-width="1.5"/>
                    <path d="M 296 106 L 304 114 M 304 106 L 296 114" stroke="#8A7E72" stroke-width="1.8" stroke-linecap="round"/>
                    <circle cx="455" cy="205" r="11" fill="#EFE9DD" stroke="#C5BBAE" stroke-width="1.5"/>
                    <path d="M 451 201 L 459 209 M 459 201 L 451 209" stroke="#8A7E72" stroke-width="1.8" stroke-linecap="round"/>
                    <circle cx="300" cy="300" r="11" fill="#EFE9DD" stroke="#C5BBAE" stroke-width="1.5"/>
                    <path d="M 296 296 L 304 304 M 304 296 L 296 304" stroke="#8A7E72" stroke-width="1.8" stroke-linecap="round"/>
                    <circle cx="155" cy="205" r="11" fill="#EFE9DD" stroke="#C5BBAE" stroke-width="1.5"/>
                    <path d="M 151 201 L 159 209 M 159 201 L 151 209" stroke="#8A7E72" stroke-width="1.8" stroke-linecap="round"/>
                  </svg>

                  <div class="s2-fragments-grid">
                    <!-- Fragment 1: AI Planning -->
                    <div class="s2-ui-fragment s2-frag-ai">
                      <div class="s2-frag-head">
                        <span class="s2-frag-icon">${renderIcon("sparkles")}</span>
                        <div>
                          <strong>1 · KI-Planung</strong>
                          <small>Generiert Text-Listen</small>
                        </div>
                      </div>
                      <div class="s2-frag-mock">
                        <div class="s2-mock-line"><span>01</span> Kapitel 1–4 zusammenfassen</div>
                        <div class="s2-mock-line"><span>02</span> 20 Übungsaufgaben rechnen</div>
                        <div class="s2-mock-status">Endet im Chat-Verlauf</div>
                      </div>
                    </div>

                    <!-- Fragment 2: Calendar -->
                    <div class="s2-ui-fragment s2-frag-cal">
                      <div class="s2-frag-head">
                        <span class="s2-frag-icon">${renderIcon("calendar")}</span>
                        <div>
                          <strong>2 · Kalender</strong>
                          <small>Isolierte Termine</small>
                        </div>
                      </div>
                      <div class="s2-frag-mock">
                        <div class="s2-mock-cal-row">
                          <span class="s2-cal-date">27. NOV</span>
                          <span class="s2-cal-title">Mathe-Klausur 09:00</span>
                        </div>
                        <div class="s2-mock-status">Kennt den Lernfortschritt nicht</div>
                      </div>
                    </div>

                    <!-- Fragment 3: To-do Management -->
                    <div class="s2-ui-fragment s2-frag-todo">
                      <div class="s2-frag-head">
                        <span class="s2-frag-icon">${renderIcon("layers")}</span>
                        <div>
                          <strong>3 · To-do-Verwaltung</strong>
                          <small>Statische Checkboxen</small>
                        </div>
                      </div>
                      <div class="s2-frag-mock">
                        <div class="s2-mock-check"><i></i> Lineare Funktionen</div>
                        <div class="s2-mock-check"><i></i> Altklausur lösen</div>
                        <div class="s2-mock-status">Manuelle Entscheidung jeden Tag</div>
                      </div>
                    </div>

                    <!-- Fragment 4: Focus Timer -->
                    <div class="s2-ui-fragment s2-frag-timer">
                      <div class="s2-frag-head">
                        <span class="s2-frag-icon">${renderIcon("clock")}</span>
                        <div>
                          <strong>4 · Fokus-Timer</strong>
                          <small>Blinde Stoppuhr</small>
                        </div>
                      </div>
                      <div class="s2-frag-mock">
                        <div class="s2-mock-timer-ring">
                          <span>25:00</span>
                        </div>
                        <div class="s2-mock-status">Kein Feedback an den Plan</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Bottom Source Bar (Outside top-right camera safe zone, crisp at 1920x1080) -->
            <footer class="s2-footer">
              ${
                hasStat
                  ? renderSourceLine({
                      source: data.problemStatisticSource,
                      note: "Selbstauskunft zu regelmäßiger Smartphone-Ablenkung bei den Hausaufgaben"
                    })
                  : ""
              }
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
