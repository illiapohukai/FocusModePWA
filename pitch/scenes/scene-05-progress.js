/**
 * Scene 5 — Product Progress & Validation
 * 2 manually controlled beats:
 *   Beat 0: Progress trajectory (Schulprojekt → PWA → AI Missions → Android Foundation → Pilot) + GEBAUT
 *   Beat 1: NOCH ZU BEWEISEN + clean placeholder handling (collapses to "Pilot vorbereitet" / "Messbare Hypothesen definiert" when tokens are unreplaced)
 */

import { renderIcon, renderPixelAstronaut } from "../components/product-replicas.js";
import { isUnresolvedPlaceholder } from "../data/pitch-content.js";

export function createScene05Progress(data) {
  const hasInterviews = !isUnresolvedPlaceholder(data.interviewCount);
  const hasSignups = !isUnresolvedPlaceholder(data.pilotSignups);
  const hasQuote = !isUnresolvedPlaceholder(data.validationQuote);
  const hasAnyValidationData = hasInterviews || hasSignups || hasQuote;

  const routeStagesHtml = data.progressRoute
    .map(
      (stage, idx) => `
      <div class="s5-stage-node ${stage.completed ? "is-completed" : "is-next-pilot"}" data-stage-id="${stage.id}">
        <div class="s5-node-track-row">
          <div class="s5-node-circle">
            ${stage.completed ? renderIcon("check") : `<span class="s5-pilot-pulse"></span>`}
          </div>
          ${idx < data.progressRoute.length - 1 ? `<div class="s5-node-connector ${idx === data.progressRoute.length - 2 ? "is-to-pilot" : "is-filled"}"></div>` : ""}
        </div>
        <div class="s5-node-meta">
          <span class="s5-node-step">${stage.completed ? `STUFE 0${idx + 1} · GEBAUT` : "NÄCHSTER SCHRITT"}</span>
          <strong class="s5-node-title">${stage.label}</strong>
          <small class="s5-node-sub">${stage.sub}</small>
        </div>
      </div>
    `
    )
    .join("");

  const builtListHtml = data.builtItems
    .map(
      (item) => `
      <li class="s5-list-item is-built">
        <span class="s5-item-bullet">${renderIcon("check")}</span>
        <div class="s5-item-copy">
          <strong>${item.title}</strong>
          <small>${item.detail}</small>
        </div>
      </li>
    `
    )
    .join("");

  const toProveListHtml = data.toProveItems
    .map(
      (item) => `
      <li class="s5-list-item is-hypothesis">
        <span class="s5-item-bullet">?</span>
        <div class="s5-item-copy">
          <strong>${item.title}</strong>
          <small>${item.detail}</small>
        </div>
      </li>
    `
    )
    .join("");

  return {
    id: "scene-05-progress",
    title: "5 · Fortschritt & Validierung",
    beatCount: 2,
    theme: "cream",
    render() {
      return `
        <section class="pitch-scene scene-05" id="scene-05-progress" data-beat="0" aria-label="Szene 5: Produktfortschritt und Validierung">
          <div class="s5-stage">
            <header class="s5-header">
              <div class="fm-eyebrow">
                <span class="fm-eyebrow-dot"></span>
                <span>ENTWICKLUNG IM PROGRAMM · EHRLICHE BILANZ</span>
              </div>
              <h2 class="s5-title">Was gebaut ist — und was der Pilot beweisen muss.</h2>
            </header>

            <!-- Top Horizontal / Orbital Progress Route with Pixel Astronaut at Android Foundation → Pilot -->
            <div class="s5-trajectory-band">
              <div class="s5-route-grid">
                ${routeStagesHtml}
              </div>
              <div class="s5-astronaut-waypoint">
                ${renderPixelAstronaut({
                  src: data.astronautAsset,
                  id: "s5Astronaut",
                  variant: "progress-waypoint"
                })}
              </div>
            </div>

            <!-- Two Distinct Sections: GEBAUT (Left) vs. NOCH ZU BEWEISEN (Right) -->
            <div class="s5-comparison-grid">
              <!-- Left Column: GEBAUT -->
              <article class="s5-column s5-col-built" id="s5ColBuilt">
                <div class="s5-col-header">
                  <span class="s5-col-tag is-built-tag">GEBAUT</span>
                  <span class="s5-col-sub">Im Repository verifizierbare Produktsubstanz</span>
                </div>
                <ul class="s5-items-list">
                  ${builtListHtml}
                </ul>
              </article>

              <!-- Right Column: NOCH ZU BEWEISEN -->
              <article class="s5-column s5-col-prove" id="s5ColProve">
                <div class="s5-col-header">
                  <span class="s5-col-tag is-prove-tag">NOCH ZU BEWEISEN</span>
                  <span class="s5-col-sub">Offene Validierungs-Hypothesen im Alltag</span>
                </div>
                <ul class="s5-items-list">
                  ${toProveListHtml}
                </ul>

                <!-- Validation Status Surface: Collapses cleanly to approved neutral copy if placeholders are unresolved -->
                <div class="s5-validation-bar" id="s5ValidationBar">
                  ${
                    hasAnyValidationData
                      ? `
                      <div class="s5-val-resolved">
                        ${hasInterviews ? `<div class="s5-val-metric"><strong>${data.interviewCount}</strong><span>Interviews</span></div>` : ""}
                        ${hasSignups ? `<div class="s5-val-metric"><strong>${data.pilotSignups}</strong><span>Pilot-Anmeldungen</span></div>` : ""}
                        ${hasQuote ? `<blockquote class="s5-val-quote">„${data.validationQuote}“</blockquote>` : ""}
                      </div>
                    `
                      : `
                      <div class="s5-val-neutral">
                        <div class="s5-val-pill is-primary">
                          <span class="s5-val-dot"></span>
                          <strong>${data.validationFallbackPrimary}</strong>
                        </div>
                        <div class="s5-val-pill is-secondary">
                          ${renderIcon("target")}
                          <strong>${data.validationFallbackSecondary}</strong>
                        </div>
                      </div>
                    `
                  }
                </div>
              </article>
            </div>
          </div>
        </section>
      `;
    },
    onBeat(el, beatIndex) {
      el.dataset.beat = String(beatIndex);
    }
  };
}
