/*
 * Scene 5 · Product progress and validation.
 * Beat route: Schulprojekt → PWA → AI Missions → Android Foundation → Pilot (outlined, next);
 *             the astronaut stands at the current position, between Android Foundation and Pilot.
 * Beat built: GEBAUT — repository-supported progress only.
 * Beat prove: NOCH ZU BEWEISEN — open hypotheses, plus validation values if they were supplied,
 *             otherwise the approved neutral copy. Raw [TOKENS] never render.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch, ui = Pitch.ui, esc = Pitch.dom.esc, icon = Pitch.icon, CT = Pitch.content, C = CT.data;

  const XS = [250, 610, 970, 1330, 1690];
  function yAt(i) { const t = i / (XS.length - 1); return Math.round(392 - 28 * (1 - Math.pow(2 * t - 1, 2))); }

  function validationStrip() {
    const items = [];
    if (CT.isResolved(C.interviewCount)) items.push('<span class="pg-val"><b>' + esc(C.interviewCount) + '</b> Interviews</span>');
    if (CT.isResolved(C.pilotSignups)) items.push('<span class="pg-val"><b>' + esc(C.pilotSignups) + '</b> Pilot-Anmeldungen</span>');
    let quote = '';
    if (CT.isResolved(C.validationQuote)) {
      quote = '<blockquote class="pg-quote">„' + esc(C.validationQuote) + '“' + (CT.isResolved(C.validationQuoteAuthor) ? '<cite>' + esc(C.validationQuoteAuthor) + '</cite>' : '') + '</blockquote>';
    }
    if (!items.length && !quote) {
      const fb = C.validationFallback || ['Pilot vorbereitet', 'Messbare Hypothesen definiert'];
      return '<div class="pg-strip is-fallback"><span class="pg-chip is-ready"><i></i>' + esc(fb[0]) + '</span><span class="pg-chip">' + icon('list-checks') + esc(fb[1]) + '</span></div>';
    }
    return '<div class="pg-strip">' + items.join('') + quote + '</div>';
  }

  function build(ctx) {
    const pts = XS.map(function (x, i) { return { x: x, y: yAt(i) }; });
    const route = C.progressRoute || [];
    let doneD = '', nextD = '';
    pts.forEach(function (p, i) {
      if (i === 0) return;
      const a = pts[i - 1], mx = (a.x + p.x) / 2;
      const seg = 'M' + a.x + ' ' + a.y + ' C ' + mx + ' ' + a.y + ', ' + mx + ' ' + p.y + ', ' + p.x + ' ' + p.y;
      if (route[i] && route[i].state === 'next') nextD += seg; else doneD += seg;
    });
    const nodes = route.map(function (r, i) {
      return '<div class="pg-node ' + (r.state === 'next' ? 'is-next' : 'is-done') + '" style="left:' + pts[i].x + 'px;top:' + pts[i].y + 'px;--i:' + i + '">' +
        '<span class="pg-dot">' + (r.state === 'next' ? '' : icon('check')) + '</span><span class="pg-lbl"><b>' + esc(r.label) + '</b><small>' + esc(r.note || '') + '</small></span></div>';
    }).join('');
    const list = function (arr, cls, ic) {
      return '<ul class="' + cls + '">' + arr.map(function (t, i) { return '<li style="--i:' + i + '"><span class="pg-li-ic">' + icon(ic) + '</span>' + esc(t) + '</li>'; }).join('') + '</ul>';
    };
    const built = C.built || { pwa: [], android: [] };

    Pitch.dom.mount(ctx.root, [
      '<div class="pg-bg cream-bg"></div>',
      '<div class="pg-head">' + ui.kicker('Fortschritt') + '<h2 class="display pg-title">Vom Schulprojekt zum Pilot.</h2></div>',
      '<svg class="pg-route" viewBox="0 0 1920 1080" aria-hidden="true"><path class="pg-done" pathLength="100" d="' + doneD + '"/><path class="pg-next" d="' + nextD + '"/></svg>',
      nodes,
      '<span class="astro-shadow pg-shadow"></span>',
      ui.astronaut('h146 pg-astro'),
      '<section class="pg-panel pg-built card"><p class="kicker">' + icon('check') + 'Gebaut</p>' +
        '<div class="pg-cols"><div><p class="pg-sub">PWA</p>' + list(built.pwa, 'pg-list', 'check') + '</div>' +
        '<div><p class="pg-sub">Android</p>' + list(built.android, 'pg-list', 'check') + '</div></div>' +
        (C.buildFact ? '<p class="pg-fact">' + icon('clock') + esc(C.buildFact) + '</p>' : '') + '</section>',
      '<section class="pg-panel pg-prove"><p class="kicker">' + icon('circle-dashed') + 'Noch zu beweisen</p>' +
        list(C.toProve || [], 'pg-list pg-open', 'circle-dashed') + validationStrip() + '</section>'
    ].join(''));
  }

  Pitch.registerScene({
    id: 'progress', number: 5, title: 'Fortschritt · Validierung', beats: ['route', 'built', 'prove'],
    assets: ['./assets/operator/orbit-standing.png'],
    tone: function () { return 'light'; },
    build: build
  });
})(window);
