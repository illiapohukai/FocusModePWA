/*
 * Scene 6 · Market and business model.
 * Beat segment: 2,9 Mio. Studierende (Destatis) as broad context only; the narrow first target group.
 * Beat compare: separated category domains (planning, organisation, focus) versus the connected
 *               Focus Mode+ workflow, with the defensible comparison statement.
 * Beat model:   Free and Pro surfaces; price hypotheses, clearly labelled, no final price.
 * No revenue projection, no MRR scenarios, no conversion assumptions.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch, ui = Pitch.ui, esc = Pitch.dom.esc, icon = Pitch.icon, C = Pitch.content.data;

  const ICONS = { spark: 'sparkles', check: 'list-checks', timer: 'timer' };

  function segmentMarkup(text) {
    const m = /^([\d.,]+)\s*(.*)$/.exec(text || '');
    return m ? '<span class="num">' + esc(m[1]) + '</span><span class="pct mk-unit">' + esc(m[2]) + '</span>' : esc(text);
  }

  function build(ctx) {
    const domains = (C.comparison || []).map(function (d, i) {
      return '<div class="mk-dom" style="--i:' + i + '"><div class="mk-orb"><i class="mk-ring"></i><i class="mk-moon"></i><span>' + icon(ICONS[d.icon] || 'sparkle') + '</span></div>' +
        '<b>' + esc(d.category) + '</b><small>' + esc(d.tools) + '</small></div>';
    }).join('');
    const self = C.comparisonSelf || { tools: 'Focus Mode+', category: 'durchgängiger Workflow' };
    const list = function (arr) { return '<ul>' + arr.map(function (t) { return '<li>' + icon('check') + esc(t) + '</li>'; }).join('') + '</ul>'; };

    Pitch.dom.mount(ctx.root, [
      '<div class="mk-bg cream-bg"></div>',
      '<div class="mk-a">',
      ui.kicker('Markt & Modell'),
      '<p class="stat-num mk-num">' + segmentMarkup(C.addressableSegment) + '</p>',
      '<p class="lead mk-label">' + esc(C.addressableSegmentLabel) + '</p>',
      ui.source(C.addressableSegmentSource, '', 'mk-source'),
      '<p class="mk-qual">' + esc(C.addressableSegmentQualifier) + '</p>',
      '<div class="mk-target"><p class="kicker">Erste Zielgruppe</p><p class="mk-pill">' + icon('target') + esc(C.targetGroup) + '</p></div>',
      '</div>',
      '<div class="mk-b">',
      '<div class="mk-doms">' + domains +
        '<svg class="mk-join" viewBox="0 0 120 40" aria-hidden="true"><path d="M6 20 H104 M90 8 L106 20 L90 32"/></svg>' +
        '<div class="mk-dom mk-self"><div class="mk-orb"><i class="mk-ring"></i>' +
          '<span class="mk-sat s1">' + icon('sparkles') + '</span><span class="mk-sat s2">' + icon('list-checks') + '</span><span class="mk-sat s3">' + icon('timer') + '</span>' +
          '<strong>' + esc(self.tools) + '</strong></div><b>' + esc(self.category) + '</b></div></div>',
      '<p class="mk-statement">' + esc(C.comparisonStatement) + '</p>',
      '</div>',
      '<div class="mk-c">',
      '<section class="mk-free card"><p class="kicker">Free</p>' + list(C.freeDefinition || []) + '</section>',
      '<section class="mk-pro mhero"><p class="kicker">Pro</p>' + list(C.proDefinition || []) +
        '<p class="mk-price-label"><b>' + esc(C.priceHypothesesLabel || 'Preishypothesen') + '</b> · ' + esc(C.priceHypothesesUnit || '') + '</p>' +
        '<div class="mk-prices">' + (C.priceHypotheses || []).map(function (p) { return '<span>' + esc(p) + '</span>'; }).join('') + '</div></section>',
      '</div>'
    ].join(''));
  }

  Pitch.registerScene({
    id: 'market', number: 6, title: 'Markt · Geschäftsmodell', beats: ['segment', 'compare', 'model'],
    tone: function () { return 'light'; },
    build: build
  });
})(window);
