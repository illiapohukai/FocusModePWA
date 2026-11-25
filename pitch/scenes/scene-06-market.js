/**
 * Scene 6 — Market, Defensible Positioning & Business Model
 * 2 manually controlled beats:
 *   Beat 0: Broad Segment Context (2,9 Mio. Studierende, Destatis WS 2024/25) → Narrow Beachhead (Studierende · 18+ · Android) + Orbital Competitor Domains
 *   Beat 1: Defensible Positioning Statement + FREE vs. PRO Business Model Surfaces & Preishypothesen (3,99 € / 5,99 € / 7,99 €)
 */

import { renderIcon, renderSourceLine } from "../components/product-replicas.js";

export function createScene06Market(data) {
  const compRowsHtml = data.competitorCategories
    .map(
      (cat) => `
      <div class="s6-comp-row ${cat.isPrimary ? "is-focus-mode" : ""}">
        <div class="s6-comp-left">
          <strong class="s6-comp-tools">${cat.tools}</strong>
          <span class="s6-comp-arrow">→</span>
          <span class="s6-comp-domain">${cat.domain}</span>
        </div>
        <small class="s6-comp-detail">${cat.detail}</small>
      </div>
    `
    )
    .join("");

  const freeListHtml = data.freeDefinition
    .map((item) => `<li>${renderIcon("check")} <span>${item}</span></li>`)
    .join("");

  const proListHtml = data.proDefinition
    .map((item) => `<li>${renderIcon("sparkles")} <span>${item}</span></li>`)
    .join("");

  const pricesHtml = data.priceHypotheses
    .map((p) => `<span class="s6-price-chip">${p}</span>`)
    .join("");

  return {
    id: "scene-06-market",
    title: "6 · Markt & Geschäftsmodell",
    beatCount: 2,
    theme: "cream",
    render() {
      return `
        <section class="pitch-scene scene-06" id="scene-06-market" data-beat="0" aria-label="Szene 6: Markt und Geschäftsmodell">
          <div class="s6-stage">
            <header class="s6-header">
              <div class="fm-eyebrow">
                <span class="fm-eyebrow-dot"></span>
                <span>MARKTKONTEXT · DIFFERENZIERUNG · GESCHÄFTSMODELL</span>
              </div>
              <h2 class="s6-title">Spitze Startzielgruppe. Durchgängiger Workflow.</h2>
            </header>

            <div class="s6-main-grid">
              <!-- Left Column: Broad Segment Context (2,9 Mio.) + Orbital Category Domains -->
              <div class="s6-left-col">
                <!-- Segment Funnel Surface (Context only, zero revenue projections) -->
                <article class="s6-segment-card">
                  <div class="s6-seg-top">
                    <div class="s6-seg-broad">
                      <span class="s6-seg-kicker">BREITER ADRESSRAHMEN (KONTEXT)</span>
                      <div class="s6-seg-num-row">
                        <strong>${data.addressableSegment}</strong>
                        <span>${data.addressableSegmentLabel}</span>
                      </div>
                    </div>
                    <div class="s6-seg-divider" aria-hidden="true">→</div>
                    <div class="s6-seg-beachhead">
                      <span class="s6-seg-kicker">ERSTE STARTZIELGRUPPE</span>
                      <strong class="s6-target-pill">${data.targetGroup}</strong>
                    </div>
                  </div>
                  <p class="s6-seg-disclaimer">${data.addressableSegmentContext}</p>
                </article>

                <!-- Separated Orbital Categories & Compact Competitor Comparison -->
                <article class="s6-positioning-card">
                  <div class="s6-orbit-domains" aria-hidden="true">
                    <div class="s6-domain-satellite s6-sat-plan">Planung<span>ChatGPT / Gemini</span></div>
                    <div class="s6-domain-satellite s6-sat-org">Organisation<span>Todoist / Notion</span></div>
                    <div class="s6-domain-satellite s6-sat-focus">Fokus<span>Forest / Opal</span></div>
                    <div class="s6-domain-center">
                      <strong>Focus Mode+</strong>
                      <span>Verbundener Orbit</span>
                    </div>
                  </div>

                  <div class="s6-comp-list">
                    ${compRowsHtml}
                  </div>

                  <p class="s6-defensible-claim">
                    ${data.defensiblePositioning}
                  </p>
                </article>
              </div>

              <!-- Right Column: Two Business-Model Surfaces (FREE vs. PRO + Preishypothesen) -->
              <div class="s6-right-col" id="s6BusinessCol">
                <!-- FREE Surface -->
                <article class="s6-tier-card s6-tier-free">
                  <div class="s6-tier-head">
                    <div>
                      <span class="s6-tier-badge">FREE</span>
                      <h3 class="s6-tier-title">Alltäglicher Fokus-Einstieg</h3>
                    </div>
                    <span class="s6-tier-price-tag">0 €</span>
                  </div>
                  <ul class="s6-tier-list">
                    ${freeListHtml}
                  </ul>
                </article>

                <!-- PRO Surface -->
                <article class="s6-tier-card s6-tier-pro">
                  <div class="s6-tier-head">
                    <div>
                      <span class="s6-tier-badge is-pro">PRO</span>
                      <h3 class="s6-tier-title">Prüfungs- &amp; Projektsteuerung</h3>
                    </div>
                  </div>
                  <ul class="s6-tier-list">
                    ${proListHtml}
                  </ul>

                  <div class="s6-price-hypotheses-box">
                    <div class="s6-ph-header">
                      <span class="s6-ph-label">${data.priceHypothesesLabel}</span>
                      <small>Im Pilot &amp; in Interviews zu testen</small>
                    </div>
                    <div class="s6-ph-chips">
                      ${pricesHtml}
                    </div>
                  </div>
                </article>
              </div>
            </div>

            <footer class="s6-footer">
              ${renderSourceLine({
                source: data.addressableSegmentSource,
                note: "Breites Adresssegment zur Einordnung — keine Kunden- oder Umsatzprognose"
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
