/**
 * Scene 4 — Product Demonstration (8 Deterministic Beats)
 *   Beat 0 (Demo 1): Mission Creation — Goal ("Mathe-Klausur vorbereiten"), Deadline ("27. November"), Material
 *   Beat 1 (Demo 2): AI Mission Generation — Pixel Operator at terminal + one-shot planet thought-cloud
 *   Beat 2 (Demo 3): Mission Route — 4-step orbital trajectory across space
 *   Beat 3 (Demo 4): Next Concrete Step — "Lineare Funktionen üben" · 25:00 isolated in depth
 *   Beat 4 (Demo 5): Distraction Protection — "Ablenkende Apps auswählen" / "Fokus-Schutz vorbereiten" / "Geschützter Fokus"
 *   Beat 5 (Demo 6): Quick Focus Launch — Quick Focus orbit → concentrated matter → Cosmic Core transition
 *   Beat 6 (Demo 7): Active Focus — Dark Cosmic Core, anchored 25:00 timer, protected-focus badge, task card
 *   Beat 7 (Demo 8): Result & Adaptation — Cosmic Core contracts into completed progress node; next step activates
 */

import { PitchCosmicCore, PitchOperatorPlayer } from "../components/focus-core.js";
import { renderIcon, renderMissionHeroCard, renderQuickFocusDial } from "../components/product-replicas.js";

const DEMO_BEAT_LABELS = [
  "1/8 · Mission Creation",
  "2/8 · AI Mission Generation",
  "3/8 · Mission Route",
  "4/8 · Nächster konkreter Schritt",
  "5/8 · Fokus-Schutz vorbereiten",
  "6/8 · Quick Focus → Cosmic Core",
  "7/8 · Aktive Fokus-Session",
  "8/8 · Ergebnis & Rückfluss"
];

export function createScene04Demo(data, options = {}) {
  const fx = data.demoFixture;
  let cosmicCore = null;
  let operatorPlayer = null;
  let launchTimeoutIds = [];

  function clearLaunchTimers() {
    launchTimeoutIds.forEach((id) => clearTimeout(id));
    launchTimeoutIds = [];
  }

  return {
    id: "scene-04-demo",
    title: "4 · Produkt-Demo",
    beatCount: 8,
    theme: "dynamic",
    render() {
      const routeNodesHtml = fx.routeSteps
        .map(
          (step, idx) => `
          <div class="s4-route-node s4-rnode-${idx + 1}" data-step-index="${step.index}" data-status="${step.status}">
            <div class="s4-rnode-marker">
              <span class="s4-rnode-num">${step.index}</span>
              <span class="s4-rnode-check">${renderIcon("check")}</span>
            </div>
            <div class="s4-rnode-card">
              <div class="s4-rnode-top">
                <span class="s4-rnode-tag">${
                  step.status === "completed"
                    ? "Abgeschlossen"
                    : step.status === "next"
                    ? "Nächster Schritt"
                    : `Schritt ${step.index}`
                }</span>
                <span class="s4-rnode-dur">${step.durationLabel || step.duration}</span>
              </div>
              <strong class="s4-rnode-title">${step.title}</strong>
              <p class="s4-rnode-detail">${step.detail}</p>
            </div>
          </div>
        `
        )
        .join("");

      const appsHtml = fx.distractionApps
        .map(
          (app) => `
          <div class="s4-app-chip ${app.selected ? "is-selected" : "is-allowed"}">
            <span class="s4-app-dot" style="background:${app.color}">${app.name.slice(0, 1)}</span>
            <div class="s4-app-copy">
              <strong>${app.name}</strong>
              <small>${app.category}</small>
            </div>
            <span class="s4-app-state">
              ${app.selected ? `${renderIcon("shieldCheck")} Fokus-Schutz` : "Erlaubt"}
            </span>
          </div>
        `
        )
        .join("");

      return `
        <section class="pitch-scene scene-04" id="scene-04-demo" data-beat="0" data-cosmic="false" aria-label="Szene 4: Produkt-Demonstration">
          <!-- Continuous Cosmic Environment Wash Layer (darkens on Beats 5 & 6, recedes on Beat 7) -->
          <div class="s4-cosmic-wash" id="s4CosmicWash" aria-hidden="true"></div>

          <div class="s4-stage">
            <!-- Top Context Header (Left-aligned, clear of 320x220 top-right camera bubble zone) -->
            <header class="s4-header">
              <div class="fm-eyebrow">
                <span class="fm-eyebrow-dot"></span>
                <span>PRODUKT-DEMONSTRATION · DETERMINISTISCHER WORKFLOW</span>
              </div>
              <div class="s4-beat-stepper" id="s4BeatStepper" aria-live="polite">
                <span class="s4-stepper-badge" id="s4StepperBadge">${DEMO_BEAT_LABELS[0]}</span>
              </div>
            </header>

            <!-- Central Spatial Choreography Viewport -->
            <div class="s4-choreo-viewport" id="s4ChoreoViewport">
              <!-- Transient Ghost Phone Outline (Appears gently only during assembly/launch to ground mobile product reality) -->
              <div class="s4-ghost-phone" id="s4GhostPhone" aria-hidden="true">
                <div class="s4-ghost-island"></div>
              </div>

              <!-- ============================================================
                   LAYER A (BEAT 0): MISSION CREATION — Floating Product Layers
                   ============================================================ -->
              <div class="s4-layer s4-layer-creation" id="s4LayerCreation">
                <div class="s4-creation-headline">
                  <h3>Aus Ziel, Termin und Material wird eine Mission.</h3>
                  <p>Kein stundenlanges Konfigurieren — drei konkrete Eingaben genügen.</p>
                </div>

                <div class="s4-creation-cluster">
                  <!-- Component 1: Goal Input Surface -->
                  <div class="s4-comp-card s4-comp-goal">
                    <div class="s4-comp-kicker">${renderIcon("target")} LERNZIEL &amp; FACH</div>
                    <div class="s4-comp-subject">${fx.goalSubject}</div>
                    <strong class="s4-comp-main-title">${fx.goalTitle}</strong>
                    <div class="s4-comp-confidence">
                      <span>Sicherheitsgefühl: <strong>Mittel verstanden</strong></span>
                      <div class="s4-conf-bar"><i style="width:58%"></i></div>
                    </div>
                  </div>

                  <!-- Component 2: Deadline & Zeitplan Trajectory Board -->
                  <div class="s4-comp-card s4-comp-deadline">
                    <div class="s4-comp-kicker">${renderIcon("calendar")} ZEITPLAN &amp; DEADLINE</div>
                    <div class="s4-deadline-hero">
                      <span class="s4-dl-date">${fx.deadlineText}</span>
                      <span class="s4-dl-badge">PRÜFUNG</span>
                    </div>
                    <div class="s4-trajectory-mini">
                      <span>START</span>
                      <div class="s4-traj-dots"><i></i><i></i><i></i><i></i></div>
                      <span>ZIEL</span>
                    </div>
                    <small class="s4-dl-sub">${fx.prepWindow}</small>
                  </div>

                  <!-- Component 3: Material Worksheet Preview -->
                  <div class="s4-comp-card s4-comp-material">
                    <div class="s4-comp-kicker">${renderIcon("fileText")} LERNMATERIAL</div>
                    <div class="s4-doc-preview">
                      <div class="s4-doc-page">
                        <span class="s4-doc-tag">PDF</span>
                        <div class="s4-doc-lines">
                          <i></i><i></i><i></i>
                          <span class="s4-doc-math">f(x) = m · x + b</span>
                        </div>
                      </div>
                      <div class="s4-doc-info">
                        <strong>${fx.materialName}</strong>
                        <small>${fx.materialMeta}</small>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- ============================================================
                   LAYER B (BEAT 1): AI MISSION GENERATION — Pixel Operator
                   ============================================================ -->
              <div class="s4-layer s4-layer-generation" id="s4LayerGeneration">
                <div class="s4-operator-card">
                  <div class="s4-operator-canvas-wrap">
                    <canvas id="s4OperatorCanvas" width="266" height="358" aria-label="Pixel-Operator strukturiert die Mission"></canvas>
                  </div>
                  <div class="s4-operator-info">
                    <span class="s4-op-eyebrow">FOCUS MISSION ENGINE</span>
                    <h3 class="s4-op-title">Deine Mission entsteht</h3>
                    <p class="s4-op-desc">Ziel, Prüfungstermin und Material werden in konkrete Fokus-Einheiten zerlegt.</p>
                    <div class="s4-op-stages" id="s4OperatorStages">
                      <div class="s4-op-stage is-active" data-stage="0">
                        <span class="s4-op-dot"></span>
                        <span>${fx.generationStages[0]}</span>
                      </div>
                      <div class="s4-op-stage" data-stage="1">
                        <span class="s4-op-dot"></span>
                        <span>${fx.generationStages[1]}</span>
                      </div>
                      <div class="s4-op-stage" data-stage="2">
                        <span class="s4-op-dot"></span>
                        <span>${fx.generationStages[2]}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- ============================================================
                   LAYER C (BEATS 2, 3 & 7): MISSION ROUTE & RESULT ADAPTATION
                   ============================================================ -->
              <div class="s4-layer s4-layer-route" id="s4LayerRoute">
                <div class="s4-route-top-row">
                  <div class="s4-route-hero-wrap" id="s4RouteHeroWrap">
                    ${renderMissionHeroCard({
                      title: fx.goalTitle,
                      subject: fx.goalSubject,
                      deadline: fx.deadlineText,
                      progressPct: 25,
                      nextStep: "Lineare Funktionen verstehen",
                      nextDuration: "25 Min."
                    })}
                  </div>

                  <!-- Beat 7 Result & Feedback Banner (Hidden until Beat 7) -->
                  <div class="s4-result-adaptation-card" id="s4ResultCard">
                    <div class="s4-res-status-row">
                      <span class="s4-res-pill is-primary">${renderIcon("check")} ${fx.resultCopy.completed}</span>
                      <span class="s4-res-pill is-secondary">${fx.resultCopy.partial}</span>
                    </div>
                    <h4 class="s4-res-heading">${fx.resultCopy.feedbackLine}</h4>
                    <p class="s4-res-copy">
                      Schritt 2 ist als Fortschrittsknoten gespeichert (50 % Gesamtfortschritt). Schritt 3 <strong>„Übungsaufgaben lösen“</strong> rückt automatisch als nächster Fokus-Schritt nach.
                    </p>
                  </div>
                </div>

                <!-- Spatial Orbital Mission Trajectory (4 Steps) -->
                <div class="s4-orbital-route-track" id="s4OrbitalRouteTrack">
                  <svg class="s4-route-spline" viewBox="0 0 1200 180" fill="none" aria-hidden="true">
                    <path
                      d="M 90 110 C 310 35, 510 35, 620 95 C 740 155, 950 140, 1110 65"
                      stroke="#D8CFC2"
                      stroke-width="3"
                      stroke-dasharray="7 7"
                    />
                    <path
                      id="s4RouteActivePath"
                      d="M 90 110 C 310 35, 510 35, 620 95"
                      stroke="#6D28D9"
                      stroke-width="4.5"
                      stroke-linecap="round"
                    />
                  </svg>
                  <div class="s4-route-nodes-grid" id="s4RouteNodesGrid">
                    ${routeNodesHtml}
                  </div>
                </div>

                <!-- Beat 3 Spotlight: Isolated Next Concrete Step (25:00) -->
                <div class="s4-next-step-spotlight" id="s4NextStepSpotlight">
                  <div class="s4-spot-card">
                    <div class="s4-spot-head">
                      <span class="s4-spot-badge">${renderIcon("target")} NÄCHSTER KONKRETER SCHRITT</span>
                      <span class="s4-spot-step">Schritt 2 von 4</span>
                    </div>
                    <div class="s4-spot-body">
                      <div class="s4-spot-copy">
                        <h3>Lineare Funktionen üben</h3>
                        <p>Funktionsgleichungen aufstellen &amp; Schnittpunkte berechnen · Übungsblatt S. 1–2</p>
                      </div>
                      <div class="s4-spot-timer-box">
                        <strong class="fm-timer-digits">25:00</strong>
                        <small>Empfohlene Fokus-Dauer</small>
                      </div>
                    </div>
                    <div class="s4-spot-foot">
                      <span>Kein erneutes Entscheiden — direkt ausführbar.</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- ============================================================
                   LAYER D (BEAT 4): DISTRACTION PROTECTION (FOKUS-SCHUTZ)
                   ============================================================ -->
              <div class="s4-layer s4-layer-protection" id="s4LayerProtection">
                <div class="s4-protection-panel">
                  <div class="s4-prot-header">
                    <div class="s4-prot-shield">${renderIcon("shieldCheck")}</div>
                    <div>
                      <span class="s4-prot-kicker">${fx.protectionCopy.prepareStatus}</span>
                      <h3>${fx.protectionCopy.selectHeading}</h3>
                    </div>
                    <span class="s4-prot-status-pill">
                      <i class="s4-prot-green-dot"></i>
                      ${fx.protectionCopy.activeBadge}
                    </span>
                  </div>

                  <p class="s4-prot-desc">
                    Vor dem Start der 25-Minuten-Einheit werden typische Unterbrechungsquellen für die Dauer der Fokus-Session markiert.
                  </p>

                  <div class="s4-apps-grid">
                    ${appsHtml}
                  </div>

                  <div class="s4-prot-footer">
                    <span>Aufgabe: <strong>Lineare Funktionen üben (25:00)</strong></span>
                    <span class="s4-prot-ready">3 Apps für Fokus-Schutz ausgewählt</span>
                  </div>
                </div>
              </div>

              <!-- ============================================================
                   LAYER E (BEATS 5, 6 & 7): SHARED OPTICAL CENTER
                   Quick Focus Dial → Matter Convergence → Cosmic Core → Progress Node
                   ============================================================ -->
              <div class="s4-layer s4-layer-focus-core" id="s4LayerFocusCore">
                <!-- Quick Focus Setup Replica (Visible at start of Beat 5, dissolves inward) -->
                <div class="s4-qf-setup-stage" id="s4QfSetupStage">
                  ${renderQuickFocusDial({
                    minutes: "25:00",
                    taskTitle: "Lineare Funktionen üben"
                  })}
                </div>

                <!-- Live 3D Cosmic Core Canvas Stage (Expands during Beat 5, active in Beat 6, contracts in Beat 7) -->
                <div class="s4-cosmic-core-stage" id="s4CosmicCoreStage">
                  <canvas id="s4CosmicCanvas" class="s4-cosmic-canvas" aria-label="Focus Mode+ Cosmic Core"></canvas>

                  <!-- Active Focus Top Header (Beat 6) -->
                  <div class="s4-active-focus-top" id="s4ActiveFocusTop">
                    <span class="s4-af-eyebrow">AKTIVE FOKUS-SESSION · MISSION SCHRITT 2/4</span>
                    <strong class="s4-af-title">Lineare Funktionen üben</strong>
                  </div>

                  <!-- Active Focus Lower Chrome: Protected Badge & Task Card (Beat 6) -->
                  <div class="s4-active-focus-bottom" id="s4ActiveFocusBottom">
                    <div class="s4-af-protect-pill">
                      <span class="s4-af-green-dot"></span>
                      <span>${fx.protectionCopy.activeBadge} aktiv · 3 Apps ausgewählt</span>
                    </div>

                    <div class="s4-af-task-card">
                      <div class="s4-af-card-head">
                        <span>${renderIcon("target")} AKTUELLER SCHRITT</span>
                        <b>25 % → 50 %</b>
                      </div>
                      <div class="s4-af-progress-bar"><i style="width:50%"></i></div>
                      <strong class="s4-af-card-title">Lineare Funktionen üben</strong>
                      <p class="s4-af-card-sub">Funktionsgleichungen aufstellen &amp; Schnittpunkte berechnen</p>
                    </div>
                  </div>
                </div>

                <!-- Anchored Shared Timer Readout (25:00): NEVER shifts coordinates between Quick Focus & Cosmic Core -->
                <div class="s4-anchored-timer" id="s4AnchoredTimer" aria-label="Fokus-Timer 25:00">
                  <strong class="fm-timer-digits" id="s4SharedTimerDigits">25:00</strong>
                </div>
              </div>
            </div>
          </div>
        </section>
      `;
    },

    onMount(el) {
      const cosmicCanvas = el.querySelector("#s4CosmicCanvas");
      if (cosmicCanvas) {
        cosmicCore = new PitchCosmicCore(cosmicCanvas, {
          mode: "core",
          reducedMotion: options.reducedMotion
        });
      }
      const opCanvas = el.querySelector("#s4OperatorCanvas");
      if (opCanvas) {
        operatorPlayer = new PitchOperatorPlayer(opCanvas, {
          reducedMotion: options.reducedMotion,
          metaUrl: data.operatorMetaAsset,
          onStageChange(stageIdx) {
            const stages = el.querySelectorAll("#s4OperatorStages .s4-op-stage");
            stages.forEach((node, i) => {
              node.classList.toggle("is-active", i === stageIdx);
              node.classList.toggle("is-done", i < stageIdx);
            });
          }
        });
        operatorPlayer.preload().catch(() => {});
      }
    },

    onBeat(el, beatIndex) {
      clearLaunchTimers();
      el.dataset.beat = String(beatIndex);
      delete el.dataset.launchPhase;

      const badge = el.querySelector("#s4StepperBadge");
      if (badge) badge.textContent = DEMO_BEAT_LABELS[beatIndex] || DEMO_BEAT_LABELS[0];

      // Manage operator animation on Beat 1
      if (beatIndex === 1) {
        if (operatorPlayer) operatorPlayer.playSequence();
      } else if (operatorPlayer) {
        operatorPlayer.stop();
      }

      // Manage Mission Route node states between Beat 2/3 (Before session) and Beat 7 (After session completion)
      const heroBar = el.querySelector(".pitch-mh-bar i");
      const heroPct = el.querySelector(".pitch-mh-pct");
      const heroNextTitle = el.querySelector(".pitch-mh-next-title span:first-child");
      const node2 = el.querySelector(".s4-rnode-2");
      const node3 = el.querySelector(".s4-rnode-3");
      const activePath = el.querySelector("#s4RouteActivePath");

      if (beatIndex === 7) {
        if (heroBar) heroBar.style.width = "50%";
        if (heroPct) heroPct.textContent = "50 %";
        if (heroNextTitle) heroNextTitle.textContent = "Übungsaufgaben lösen";
        if (node2) {
          node2.dataset.status = "completed";
          const tag = node2.querySelector(".s4-rnode-tag");
          if (tag) tag.textContent = "Abgeschlossen · +25 Min.";
        }
        if (node3) {
          node3.dataset.status = "next";
          const tag = node3.querySelector(".s4-rnode-tag");
          if (tag) tag.textContent = "Jetzt aktiv";
        }
        if (activePath) {
          activePath.setAttribute("d", "M 90 110 C 310 35, 510 35, 620 95 C 740 155, 820 148, 860 120");
        }
      } else {
        if (heroBar) heroBar.style.width = "25%";
        if (heroPct) heroPct.textContent = "25 %";
        if (heroNextTitle) heroNextTitle.textContent = "Lineare Funktionen verstehen";
        if (node2) {
          node2.dataset.status = "next";
          const tag = node2.querySelector(".s4-rnode-tag");
          if (tag) tag.textContent = "Nächster Schritt";
        }
        if (node3) {
          node3.dataset.status = "upcoming";
          const tag = node3.querySelector(".s4-rnode-tag");
          if (tag) tag.textContent = "Schritt 3";
        }
        if (activePath) {
          activePath.setAttribute("d", "M 90 110 C 310 35, 510 35, 620 95");
        }
      }

      // Manage Cosmic Core transformation (Beat 5: Quick Focus → Cosmic Core; Beat 6: Active Core)
      if (beatIndex === 5) {
        // Choreographed multi-stage transition inside Beat 5:
        // Phase "dial" (0..360ms): Light Quick Focus dial intensifies on warm cream background
        // Phase "converge" (360..1150ms): Orbit collapses inward, background darkens, dark seed forms
        // Phase "core" (1150ms+): Full Cosmic Core shines beneath anchored 25:00 timer
        if (options.reducedMotion) {
          el.dataset.cosmic = "true";
          el.dataset.launchPhase = "core";
          document.documentElement.dataset.cosmicStage = "true";
          if (cosmicCore) {
            cosmicCore.measure();
            cosmicCore.start();
          }
        } else {
          el.dataset.cosmic = "false";
          el.dataset.launchPhase = "dial";
          document.documentElement.dataset.cosmicStage = "false";
          if (cosmicCore) {
            cosmicCore.measure();
            cosmicCore.reset();
          }
          launchTimeoutIds.push(
            setTimeout(() => {
              el.dataset.launchPhase = "converge";
              el.dataset.cosmic = "true";
              document.documentElement.dataset.cosmicStage = "true";
              if (cosmicCore) cosmicCore.triggerConvergenceBirth();
            }, 360)
          );
          launchTimeoutIds.push(
            setTimeout(() => {
              el.dataset.launchPhase = "core";
            }, 1150)
          );
        }
      } else if (beatIndex === 6) {
        el.dataset.cosmic = "true";
        el.dataset.launchPhase = "active";
        document.documentElement.dataset.cosmicStage = "true";
        if (cosmicCore) {
          cosmicCore.measure();
          cosmicCore.converging = false;
          cosmicCore.coreScale = 1;
          cosmicCore.start();
        }
      } else {
        el.dataset.cosmic = "false";
        document.documentElement.dataset.cosmicStage = "false";
        if (cosmicCore) cosmicCore.pause();
      }
    },

    onPause() {
      clearLaunchTimers();
      if (cosmicCore) cosmicCore.pause();
      if (operatorPlayer) operatorPlayer.stop();
    },

    onResume(el, beatIndex) {
      if ((beatIndex === 5 || beatIndex === 6) && cosmicCore) {
        cosmicCore.start();
      }
    },

    onExit() {
      clearLaunchTimers();
      if (cosmicCore) cosmicCore.pause();
      if (operatorPlayer) operatorPlayer.stop();
    }
  };
}
