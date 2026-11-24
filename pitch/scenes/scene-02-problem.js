/*
 * Scene 2 · Problem.
 * Beat stat:      44 % (JIM-Studie 2025) with ten learner markers; a phone-shaped interruption
 *                 propagates through the highlighted share (4 full markers, the fifth 40 % filled).
 * Beat fragments: four disconnected functional fragments (KI-Chat, Kalender, To-do, Fokus-Timer).
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch, ui = Pitch.ui, esc = Pitch.dom.esc, icon = Pitch.icon, C = Pitch.content.data;

  /* Shared with scene 3: count a number inside a formatted string up to its exact final text. */
  function countUp(ctx, el, finalText, dur, instant) {
    const m = /(\d+)(?:,(\d+))?/.exec(finalText);
    if (!m || instant || ctx.reduced()) { el.textContent = finalText; return; }
    const target = parseFloat(m[1] + '.' + (m[2] || '0')), decimals = m[2] ? m[2].length : 0;
    const before = finalText.slice(0, m.index), after = finalText.slice(m.index + m[0].length);
    ctx.tween({ dur: dur, ease: 'outQuint', update: function (e, k) {
      const v = k >= 1 ? null : (target * e).toFixed(decimals).replace('.', ',');
      el.textContent = v === null ? finalText : before + v + after;
    } });
  }
  Pitch.countUp = countUp;

  function statMarkup(text) {
    const m = /^(.*?)(\s?%)$/.exec(text);
    return m ? '<span class="num">' + esc(m[1]) + '</span><span class="pct">%</span>' : '<span class="num">' + esc(text) + '</span>';
  }
  Pitch.statMarkup = statMarkup;

  function markers(share) {
    const full = Math.floor(share * 10 + 1e-9), part = share * 10 - full;
    let out = '';
    for (let i = 0; i < 10; i++) {
      const fill = i < full ? 1 : (i === full ? part : 0);
      out += '<span class="mk' + (fill > 0 ? ' is-hit' : '') + '" style="--i:' + i + ';--fill:' + fill.toFixed(2) + '"><i class="mk-head"></i><i class="mk-body"></i>' +
        '<span class="mk-fill"><i class="mk-head"></i><i class="mk-body"></i></span></span>';
    }
    return out;
  }

  function build(ctx) {
    const share = Number(C.problemStatisticValue) || 0.44;
    Pitch.dom.mount(ctx.root, [
      '<div class="pb-bg cream-bg"></div>',
      '<div class="pb-left">',
      ui.kicker('Das Problem'),
      '<p class="stat-num pb-num">' + statMarkup(C.problemStatistic) + '</p>',
      '<p class="lead pb-label">' + esc(C.problemStatisticLabel) + '</p>',
      '<div class="pb-row"><span class="pb-phone">' + icon('smartphone') + '<i></i></span><span class="pb-wave"></span><div class="pb-markers">' + markers(share) + '</div></div>',
      '<p class="body pb-share">' + esc(String(Math.round(share * 100) / 10).replace('.', ',')) + ' von 10 · ' + esc(C.problemStatisticQualifier || 'Selbstauskunft') + '</p>',
      ui.source(C.problemStatisticSource, '', 'pb-source'),
      '</div>',
      '<svg class="pb-links frag-links" viewBox="0 0 1920 1080" aria-hidden="true"></svg>',
      ui.fragAi('pb-ai'), ui.fragCal('pb-cal'), ui.fragTodo('pb-todo'), ui.fragTimer('pb-timer'),
      '<div class="pb-say"><p class="pb-say-h">Vier Werkzeuge. Kein gemeinsamer Plan.</p><p class="body">Den nächsten Schritt wählt man selbst. Kein Ergebnis fließt zurück.</p></div>'
    ].join(''));
  }

  /* Broken connectors: two dashed stubs per pair that point at each other and never meet. */
  function brokenLinks(svg, boxes, pairs) {
    function exit(b, tx, ty) {
      const cx = b.x + b.w / 2, cy = b.y + b.h / 2, dx = tx - cx, dy = ty - cy;
      const k = Math.min(Math.abs((b.w / 2 + 14) / (dx || 1e-6)), Math.abs((b.h / 2 + 14) / (dy || 1e-6)));
      return [cx + dx * k, cy + dy * k];
    }
    let d = '';
    pairs.forEach(function (pr) {
      const a = boxes[pr[0]], b = boxes[pr[1]];
      const ca = [a.x + a.w / 2, a.y + a.h / 2], cb = [b.x + b.w / 2, b.y + b.h / 2];
      const pa = exit(a, cb[0], cb[1]), pb = exit(b, ca[0], ca[1]);
      const vx = pb[0] - pa[0], vy = pb[1] - pa[1];
      d += 'M' + pa[0].toFixed(1) + ' ' + pa[1].toFixed(1) + 'L' + (pa[0] + vx * 0.36).toFixed(1) + ' ' + (pa[1] + vy * 0.36).toFixed(1);
      d += 'M' + pb[0].toFixed(1) + ' ' + pb[1].toFixed(1) + 'L' + (pb[0] - vx * 0.36).toFixed(1) + ' ' + (pb[1] - vy * 0.36).toFixed(1);
    });
    svg.innerHTML = '<path d="' + d + '"/>';
  }
  Pitch.brokenLinks = brokenLinks;

  function boxOf(el) { return { x: el.offsetLeft, y: el.offsetTop, w: el.offsetWidth, h: el.offsetHeight }; }

  function resize(ctx) {
    const b = ['.pb-ai', '.pb-cal', '.pb-todo', '.pb-timer'].map(function (s) { return boxOf(ctx.q(s)); });
    brokenLinks(ctx.q('.pb-links'), b, [[0, 1], [0, 2], [1, 3], [2, 3]]);
  }

  function apply(ctx, beat, opts) {
    if (beat === 0) {
      countUp(ctx, ctx.q('.pb-num .num'), /^(.*?)(\s?%)$/.test(C.problemStatistic) ? C.problemStatistic.replace(/\s?%$/, '') : C.problemStatistic, 1300, opts.instant);
      Pitch.seq.run(ctx, ctx.root, [[900, 'w-go']], opts.instant);
    }
    if (beat >= 1 && opts.instant) ctx.root.classList.add('w-go');
  }

  function reset(ctx) {
    Pitch.seq.clear(ctx.root, ['w-go']);
    const n = ctx.q('.pb-num .num');
    if (n) n.textContent = C.problemStatistic.replace(/\s?%$/, '');
  }

  Pitch.registerScene({
    id: 'problem', number: 2, title: 'Problem', beats: ['stat', 'fragments'],
    tone: function () { return 'light'; },
    build: build, apply: apply, reset: reset, resize: resize,
    enter: function (ctx) { Pitch.phaseLock(ctx.qa('.frag')); }
  });
})(window);
