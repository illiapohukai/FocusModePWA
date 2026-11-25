/**
 * Presentation-Native Focus Mode+ DOM Component Replicas
 * Offline-safe SVG icons + authentic PWA visual surfaces (warm cream, deep aubergine,
 * Quick Focus dial, Mission Hero card, Mission Route, App-Selection chips, Astronaut sprite).
 */

export function renderIcon(name, className = "fm-icon") {
  const paths = {
    target: `<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.5"/>`,
    calendar: `<rect x="3" y="4" width="18" height="17" rx="3"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>`,
    fileText: `<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="13" y2="17"/>`,
    sparkles: `<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z"/><path d="M5 3l.8 2.2L8 6l-2.2.8L5 9l-.8-2.2L2 6l2.2-.8L5 3z"/>`,
    shield: `<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>`,
    shieldCheck: `<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/>`,
    check: `<polyline points="20 6 9 17 4 12"/>`,
    play: `<polygon points="6 4 20 12 6 20 6 4" fill="currentColor"/>`,
    clock: `<circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15.5 14"/>`,
    bellOff: `<path d="M13.73 21a2 2 0 0 1-3.46 0"/><path d="M18.63 13A17.89 17.89 0 0 1 18 8"/><path d="M6.26 6.26A5.86 5.86 0 0 0 6 8c0 7-3 9-3 9h14"/><path d="M18 8a6 6 0 0 0-9.33-5"/><line x1="2" y1="2" x2="22" y2="22"/>`,
    smartphone: `<rect x="5" y="2" width="14" height="20" rx="3"/><line x1="12" y1="18" x2="12.01" y2="18"/>`,
    arrowRight: `<line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>`,
    refresh: `<polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>`,
    layers: `<polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/>`
  };
  return `<svg class="${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.target}</svg>`;
}

/**
 * Renders the approved standing pixel astronaut with strict pixel-grid preservation.
 * Only whole-sprite translation, 1-2 deg tilt, shadow and ambient particles are used.
 */
export function renderPixelAstronaut({ src, id = "", roleLabel = "Operator", variant = "default" }) {
  return `
    <div class="fm-astronaut-stage fm-astro-${variant}" ${id ? `id="${id}"` : ""} aria-hidden="true">
      <div class="fm-astronaut-aura"></div>
      <div class="fm-astronaut-particles">
        <i></i><i></i><i></i>
      </div>
      <img
        src="${src}"
        alt="${roleLabel}"
        class="fm-astronaut-sprite"
        draggable="false"
      />
      <div class="fm-astronaut-shadow"></div>
    </div>
  `;
}

/**
 * Renders a compact, 1080p-legible source citation bar anchored outside the camera safe zone.
 */
export function renderSourceLine({ source, note = "", theme = "light" }) {
  return `
    <div class="fm-source-bar fm-source-${theme}">
      <span class="fm-source-dot" aria-hidden="true"></span>
      <span class="fm-source-main">Quelle: <strong>${source}</strong></span>
      ${note ? `<span class="fm-source-sep" aria-hidden="true">·</span><span class="fm-source-note">${note}</span>` : ""}
    </div>
  `;
}

/**
 * Renders the authentic Focus Mode+ Quick Focus circular duration dial replica (v17.16).
 */
export function renderQuickFocusDial({ minutes = "25:00", taskTitle = "Lineare Funktionen üben" }) {
  // 24 ticks around the dial face
  let ticks = "";
  for (let i = 0; i < 24; i++) {
    const angle = (i * 15 * Math.PI) / 180;
    const isMajor = i % 3 === 0;
    const r1 = isMajor ? 95 : 98;
    const r2 = 104;
    const x1 = (120 + Math.cos(angle) * r1).toFixed(2);
    const y1 = (120 + Math.sin(angle) * r1).toFixed(2);
    const x2 = (120 + Math.cos(angle) * r2).toFixed(2);
    const y2 = (120 + Math.sin(angle) * r2).toFixed(2);
    ticks += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" class="pitch-qb-tick ${isMajor ? "is-major" : ""}" />`;
  }

  return `
    <div class="pitch-qf-shell" id="demoQuickFocusShell">
      <div class="pitch-qf-header">
        <span class="pitch-qf-kicker">${renderIcon("target")} NÄCHSTER MISSION-SCHRITT</span>
        <strong class="pitch-qf-task">${taskTitle}</strong>
      </div>

      <div class="pitch-qb-dial" id="demoQuickDial" data-intensity="3">
        <!-- Orbit dust rings matching styles/quick-focus.css -->
        <div class="pitch-qb-orbit-layer" id="demoQuickOrbitLayer">
          <div class="pitch-qb-orbit-line"></div>
          <div class="pitch-qb-orbit-tilt"></div>
          <div class="pitch-qb-orbit pitch-qb-orbit-a"><i></i><i></i></div>
          <div class="pitch-qb-orbit pitch-qb-orbit-b"><i></i><i></i></div>
          <div class="pitch-qb-orbit pitch-qb-orbit-c"><i></i><i></i></div>
        </div>

        <div class="pitch-qb-face" id="demoQuickDialFace">
          <div class="pitch-qb-core-glow"></div>
          <svg viewBox="0 0 240 240" aria-hidden="true">
            <defs>
              <linearGradient id="pitchQbTrackGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#E5DDD0"/>
                <stop offset="100%" stop-color="#EFE9DE"/>
              </linearGradient>
              <linearGradient id="pitchQbArcGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#8B5CF6"/>
                <stop offset="55%" stop-color="#6D28D9"/>
                <stop offset="100%" stop-color="#4C1D95"/>
              </linearGradient>
            </defs>
            ${ticks}
            <circle cx="120" cy="120" r="82" class="pitch-qb-track"/>
            <circle cx="120" cy="120" r="82" class="pitch-qb-arc" stroke-dasharray="515" stroke-dashoffset="165" transform="rotate(-90 120 120)"/>
            <g class="pitch-qb-thumb" transform="rotate(152 120 120)">
              <circle cx="120" cy="38" r="16" fill="rgba(109,40,217,0.16)"/>
              <circle cx="120" cy="38" r="10.5" fill="#FFFDF9" stroke="#7C3AED" stroke-width="3"/>
              <circle cx="120" cy="38" r="4" fill="#6D28D9"/>
            </g>
          </svg>
        </div>
      </div>

      <div class="pitch-qf-footer">
        <div class="pitch-qf-protect-pill">
          <span class="pitch-qf-protect-icon">${renderIcon("shieldCheck")}</span>
          <span>Geschützter Fokus vorbereitet · 3 Apps ausgewählt</span>
        </div>
        <div class="pitch-qf-launch-btn" id="demoLaunchBtn">
          ${renderIcon("play")}
          <span>Fokus starten</span>
        </div>
      </div>
    </div>
  `;
}

/**
 * Renders the Deep Aubergine Mission Hero Card replica (matching .mission-hero-v116).
 */
export function renderMissionHeroCard({ title, subject, deadline, progressPct = 25, nextStep, nextDuration }) {
  return `
    <div class="pitch-mission-hero">
      <div class="pitch-mh-top">
        <span class="pitch-mh-badge">${renderIcon("sparkles")} FOCUS MISSION</span>
        <span class="pitch-mh-deadline">${renderIcon("calendar")} ${deadline}</span>
      </div>
      <div class="pitch-mh-subject">${subject}</div>
      <h3 class="pitch-mh-title">${title}</h3>
      <div class="pitch-mh-progress-row">
        <div class="pitch-mh-bar"><i style="width:${progressPct}%"></i></div>
        <span class="pitch-mh-pct">${progressPct} %</span>
      </div>
      <div class="pitch-mh-next-box">
        <div class="pitch-mh-next-meta">NÄCHSTER KONKRETER SCHRITT · ${nextDuration}</div>
        <div class="pitch-mh-next-title">
          <span>${nextStep}</span>
          <span class="pitch-mh-next-arrow">${renderIcon("arrowRight")}</span>
        </div>
      </div>
    </div>
  `;
}
