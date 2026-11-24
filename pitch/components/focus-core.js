/*
 * Focus Mode+ Pitch · Cosmic Core renderer.
 *
 * A condensed, pitch-only port of the PWA Cosmic Core (src/core/cosmic-core.js, v17.9+): the same
 * seeded value noise, the same lit-sphere shading with a darker readability hollow behind the timer
 * digits, the same atmosphere, the same five asymmetric orbits (radius, incline, roll, period,
 * direction) drawn as tapered ribbons with exact front/rear depth ordering, orbit dust, inner
 * stars and the deep-field backdrop. Nothing here depends on the PWA runtime.
 *
 * Added for the pitch, all as closed-form functions of explicit parameters (deterministic, no
 * simulation state): inflow (dial matter spiralling inward), seed (the compact sphere), growth,
 * contraction into a Mission progress node. Scene 7 reuses the same core as the pilot portal.
 *
 * The timer is never drawn here: it stays real HTML text above the canvas.
 */
(function (global) {
  'use strict';

  const Pitch = global.Pitch = global.Pitch || {};
  const TAU = Math.PI * 2;
  const CAMERA = 5.6;
  const CORE_SPAN = 1.55;
  const DOT_CELL = 32;
  const DOT_SPREAD = 2.9;
  const ARC_STEP = TAU / 120;
  const ARC_POINTS = 140;
  const SEED = 0x17c09e;
  const ACCENT = [109, 40, 217]; // aubergine Mission tone

  // Warm white, cool white, pale violet, lavender, restrained purple, accent (PWA palette).
  const PALETTE = [[255, 244, 232], [238, 240, 255], [224, 214, 255], [196, 181, 253], [167, 139, 250], [158, 198, 246]];

  // PWA orbit system (radii in core radii, roll in degrees, flip -1: near side passes above).
  const ORBITS = [
    { id: 'primary', radius: 1.56, incline: 0.3, roll: 7, flip: 1, period: 30, dir: 1,
      line: { width: 2.2, alpha: 0.95, glow: 2, core: [240, 232, 255], halo: [150, 108, 255] },
      crests: [{ phase: 0.9, span: 1.5, width: 1.45, gain: 0.9 }, { phase: 3.9, span: 0.9, width: 1.2, gain: 0.45 }],
      form: { at: 0, span: 0.55, from: 'rear' },
      dust: { count: 300, spread: 0.06, lift: 0.03, clumps: 3, halo: 0.36, size: 1, alpha: 1 } },
    { id: 'inclined', radius: 1.36, incline: 0.6, roll: -56, flip: 1, period: 19, dir: -1,
      line: { width: 1, alpha: 0.5, glow: 1, core: [214, 198, 255], halo: [139, 92, 246] },
      crests: [{ phase: 2.2, span: 1.1, width: 1.3, gain: 0.8 }],
      form: { at: 0.12, span: 0.5, from: 'sweep', start: 4.3 },
      dust: { count: 120, spread: 0.045, lift: 0.016, clumps: 2, halo: 0.2, size: 0.88, alpha: 0.9 } },
    { id: 'steep', radius: 1.5, incline: 0.5, roll: 63, flip: 1, period: 15, dir: 1,
      line: { width: 0.8, alpha: 0.34, glow: 1, core: [198, 184, 253], halo: [124, 92, 246] },
      crests: [{ phase: 5.1, span: 0.9, width: 1.3, gain: 0.8 }],
      form: { at: 0.24, span: 0.48, from: 'sweep', start: 4.9 },
      dust: { count: 80, spread: 0.04, lift: 0.014, clumps: 1, halo: 0.2, size: 0.82, alpha: 0.78 } },
    { id: 'arc', radius: 1.5, incline: 0.42, roll: -22, flip: -1, period: 36, dir: -1,
      line: { width: 1.15, alpha: 0.5, glow: 1, core: [226, 216, 255], halo: [150, 120, 250] },
      arc: { phase: 5.6, span: 2.5, taper: 1 },
      form: { at: 0.36, span: 0.46 },
      dust: { count: 130, spread: 0.11, lift: 0.05, size: 0.95, alpha: 1, accent: 0.5 } },
    { id: 'stream', radius: 1.12, incline: 0.5, roll: -5, flip: -1, period: 11, dir: 1,
      form: { at: 0.3, span: 0.5 },
      dust: { count: 50, spread: 0.028, lift: 0.01, clumps: 2, size: 0.7, alpha: 0.82 } }
  ];

  const HERO_STARS = [
    [0.42, -1.62, 1.5], [1.38, -1.52, 1.7], [-1.42, -1.04, 1.6], [1.5, -0.6, 1.85], [-1.5, 0.72, 1.9],
    [1.34, 1.26, 1.55], [-0.66, 1.52, 1.35], [-1.16, -1.74, 1.25], [2.3, -0.92, 1.5], [-2.36, 0.3, 1.4],
    [2.2, 1.04, 1.3], [-2.1, -1.3, 1.45]
  ];

  /* ------------------------------------------------------------------ helpers (PWA) */
  function clamp01(v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
  function smooth(e0, e1, v) { const k = clamp01((v - e0) / (e1 - e0)); return k * k * (3 - 2 * k); }
  function easeOutCubic(k) { const m = 1 - k; return 1 - m * m * m; }
  function easeInCubic(k) { return k * k * k; }
  function mix(a, b, k) { return a + (b - a) * k; }
  function rgba(c, a) { return 'rgba(' + Math.round(c[0]) + ',' + Math.round(c[1]) + ',' + Math.round(c[2]) + ',' + clamp01(a).toFixed(3) + ')'; }
  function mulberry32(seed) {
    let a = seed >>> 0;
    return function () {
      a = (a + 0x6d2b79f5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function gauss(rnd) { return (rnd() + rnd() + rnd() + rnd() - 2) * 0.87; }
  function createNoise(rnd) {
    const perm = new Uint8Array(512), val = new Float32Array(256);
    for (let i = 0; i < 256; i++) { perm[i] = i; val[i] = rnd(); }
    for (let i = 255; i > 0; i--) { const j = (rnd() * (i + 1)) | 0; const t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
    for (let i = 0; i < 256; i++) perm[256 + i] = perm[i];
    function at(x, y) {
      const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
      const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
      const X = xi & 255, Y = yi & 255;
      const a = val[perm[X + perm[Y]]], b = val[perm[X + 1 + perm[Y]]];
      const c = val[perm[X + perm[Y + 1]]], d = val[perm[X + 1 + perm[Y + 1]]];
      return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }
    return {
      fbm: function (x, y, octaves) {
        let sum = 0, amp = 0.5, norm = 0;
        for (let o = 0; o < octaves; o++) { sum += amp * at(x, y); norm += amp; x = x * 2.03 + 17.1; y = y * 2.03 - 9.7; amp *= 0.5; }
        return sum / norm;
      }
    };
  }
  function createCanvas(w, h) {
    const c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(w)); c.height = Math.max(1, Math.round(h));
    return c;
  }
  function releaseCanvas(c) { if (c) { c.width = 0; c.height = 0; } }

  /* ------------------------------------------------------------------ sprites */
  function bakeDots() {
    const c = createCanvas(DOT_CELL * PALETTE.length, DOT_CELL), ctx = c.getContext('2d'), half = DOT_CELL / 2;
    PALETTE.forEach(function (col, i) {
      const x = i * DOT_CELL + half;
      const g = ctx.createRadialGradient(x, half, 0, x, half, half);
      g.addColorStop(0, rgba(col, 1)); g.addColorStop(0.26, rgba(col, 0.94)); g.addColorStop(0.42, rgba(col, 0.3));
      g.addColorStop(0.68, rgba(col, 0.07)); g.addColorStop(1, rgba(col, 0));
      ctx.fillStyle = g; ctx.fillRect(i * DOT_CELL, 0, DOT_CELL, DOT_CELL);
    });
    return c;
  }
  function bakeGlow(color) {
    const c = createCanvas(128, 128), ctx = c.getContext('2d');
    const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    g.addColorStop(0, rgba(color, 0.6)); g.addColorStop(0.22, rgba(color, 0.3)); g.addColorStop(0.5, rgba(color, 0.09)); g.addColorStop(1, rgba(color, 0));
    ctx.fillStyle = g; ctx.fillRect(0, 0, 128, 128);
    return c;
  }
  function bakeCloud(noise) {
    const s = 128, c = createCanvas(s, s), ctx = c.getContext('2d'), img = ctx.createImageData(s, s), d = img.data;
    for (let j = 0; j < s; j++) for (let i = 0; i < s; i++) {
      const u = (i + 0.5) / s * 2 - 1, v = (j + 0.5) / s * 2 - 1, r = Math.sqrt(u * u + v * v);
      const n = noise.fbm(u * 2.2 + 5.3, v * 2.2 - 2.1, 4);
      const a = smooth(1, 0.15, r) * smooth(0.35, 0.85, n);
      const idx = (j * s + i) * 4;
      d[idx] = 168; d[idx + 1] = 128; d[idx + 2] = 255; d[idx + 3] = 255 * a;
    }
    ctx.putImageData(img, 0, 0);
    return c;
  }

  /* Lit sphere with a darker hollow behind the digits (zone in core radii). */
  function bakeCore(R, dpr, noise, zone) {
    const size = Math.ceil(R * CORE_SPAN * 2 * dpr);
    const q = Math.max(160, Math.min(360, Math.round(R * 2 * dpr * 0.72)));
    const body = createCanvas(q, q), bodyCtx = body.getContext('2d');
    const dark = createCanvas(q, q), darkCtx = dark.getContext('2d');
    const bodyImg = bodyCtx.createImageData(q, q), darkImg = darkCtx.createImageData(q, q);
    const bd = bodyImg.data, dd = darkImg.data;
    const zcx = zone.cx, zcy = zone.cy, zrx = Math.max(0.5, zone.rx), zry = Math.max(0.34, zone.ry * 1.1);
    const primary = ORBITS[0];
    const hx = -0.3, hy = -0.66, hz = Math.sqrt(1 - hx * hx - hy * hy);
    for (let j = 0; j < q; j++) {
      const v0 = (j + 0.5) / q * 2 - 1;
      for (let i = 0; i < q; i++) {
        const u0 = (i + 0.5) / q * 2 - 1, r = Math.sqrt(u0 * u0 + v0 * v0), idx = (j * q + i) * 4;
        if (r > 1.06) continue;
        const u = r > 1 ? u0 / r : u0, v = r > 1 ? v0 / r : v0, rc = r > 1 ? 1 : r;
        const nz = Math.sqrt(Math.max(0, 1 - rc * rc));
        const ndl = -0.52 * u - 0.85 * v - 0.06 * nz;
        const lit = smooth(-0.35, 0.85, ndl), rimLit = smooth(-0.2, 0.7, ndl);
        const fres = Math.pow(1 - nz, 2.4);
        const k = rc > 0.0001 ? Math.asin(rc) / rc : 1, tu = u * k, tv = v * k;
        const n1 = noise.fbm(tu * 1.5 + 3.1, tv * 1.5 - 1.4, 4);
        const n2 = noise.fbm(tu * 3.4 - 7.7 + n1 * 0.9, tv * 3.4 + 2.2 - n1 * 0.6, 3);
        const cloud = smooth(0.36, 0.8, n1 * 0.62 + n2 * 0.38);
        const ex = (u - zcx) / zrx, ey = (v - zcy) / zry;
        const hollow = 1 - smooth(0.62, 1.4, Math.sqrt(ex * ex + ey * ey));
        const depth = 0.5 + 0.5 * Math.pow(nz, 0.6);
        const nb = cloud * (0.26 + 0.74 * lit) * (1 - 0.88 * hollow) * depth;
        const hot = nb * cloud * lit;
        const rim = fres * (0.12 + 0.88 * rimLit), edge = fres * fres * rimLit;
        const counter = fres * smooth(0.15, 0.9, -ndl) * 0.3;
        const lens = Math.exp(-Math.pow((rc - 0.87) / 0.014, 2)) * (0.05 + 0.2 * lit);
        const ringY = primary.incline * primary.radius * 1.28 * Math.sqrt(Math.max(0, 1 - Math.pow(u / 1.7, 2))) + 0.12 * u;
        const sheen = Math.exp(-Math.pow((v - ringY + 0.05) / 0.13, 2)) * 0.2 * Math.pow(nz, 0.4);
        const nh = Math.max(0, u * hx + v * hy + nz * hz);
        const glint = Math.pow(nh, 620) + Math.pow(nh, 40) * 0.17;
        const baseR = 5 + 24 * lit * depth, baseG = 4 + 13 * lit * depth, baseB = 15 + 64 * lit * depth;
        bd[idx] = Math.min(255, baseR + 108 * nb + 96 * hot + 150 * rim + 96 * edge + 62 * counter + 170 * lens + ACCENT[0] * sheen * 0.5 + 62 * sheen + 255 * glint);
        bd[idx + 1] = Math.min(255, baseG + 64 * nb + 84 * hot + 116 * rim + 110 * edge + 24 * counter + 150 * lens + ACCENT[1] * sheen * 0.5 + 38 * sheen + 250 * glint);
        bd[idx + 2] = Math.min(255, baseB + 226 * nb + 30 * hot + 250 * rim + 20 * edge + 112 * counter + 255 * lens + ACCENT[2] * sheen * 0.5 + 118 * sheen + 255 * glint);
        bd[idx + 3] = 255;
        dd[idx] = Math.min(255, baseR * 0.55 + 50 * rim);
        dd[idx + 1] = Math.min(255, baseG * 0.55 + 36 * rim);
        dd[idx + 2] = Math.min(255, baseB * 0.6 + 96 * rim);
        dd[idx + 3] = 255;
      }
    }
    bodyCtx.putImageData(bodyImg, 0, 0);
    darkCtx.putImageData(darkImg, 0, 0);

    const hq = 160, halo = createCanvas(hq, hq), haloCtx = halo.getContext('2d');
    const haloImg = haloCtx.createImageData(hq, hq), hd = haloImg.data;
    for (let j = 0; j < hq; j++) {
      const v = ((j + 0.5) / hq * 2 - 1) * CORE_SPAN;
      for (let i = 0; i < hq; i++) {
        const u = ((i + 0.5) / hq * 2 - 1) * CORE_SPAN;
        const r = Math.sqrt(u * u + v * v) || 0.0001, d = Math.max(0, r - 1);
        const toward = smooth(-0.5, 0.9, (-0.52 * u - 0.85 * v) / r);
        let a = Math.exp(-d / 0.05) * (0.08 + 0.6 * toward) + Math.exp(-d / 0.2) * (0.03 + 0.13 * toward) + Math.exp(-d / 0.46) * 0.028;
        a *= smooth(CORE_SPAN, CORE_SPAN - 0.3, r) * smooth(0.86, 1, r);
        const idx = (j * hq + i) * 4;
        hd[idx] = 152 + 40 * toward; hd[idx + 1] = 114 + 44 * toward; hd[idx + 2] = 255; hd[idx + 3] = 255 * clamp01(a);
      }
    }
    haloCtx.putImageData(haloImg, 0, 0);

    function compose(interior, haloAlpha, rimAlpha) {
      const canvas = createCanvas(size, size), ctx = canvas.getContext('2d');
      const c = size / 2, rad = R * dpr;
      ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
      ctx.globalAlpha = haloAlpha; ctx.drawImage(halo, 0, 0, size, size); ctx.globalAlpha = 1;
      ctx.save(); ctx.beginPath(); ctx.arc(c, c, rad, 0, TAU); ctx.clip();
      ctx.drawImage(interior, c - rad, c - rad, rad * 2, rad * 2); ctx.restore();
      const ring = createCanvas(size, size), rctx = ring.getContext('2d');
      rctx.strokeStyle = rgba([238, 228, 255], rimAlpha); rctx.lineWidth = 1.4 * dpr;
      rctx.beginPath(); rctx.arc(c, c, rad - 0.7 * dpr, 0, TAU); rctx.stroke();
      const mask = rctx.createLinearGradient(c - 0.52 * rad, c - 0.85 * rad, c + 0.16 * rad, c + 0.26 * rad);
      mask.addColorStop(0, 'rgba(0,0,0,1)'); mask.addColorStop(0.45, 'rgba(0,0,0,0.5)'); mask.addColorStop(1, 'rgba(0,0,0,0)');
      rctx.globalCompositeOperation = 'destination-in'; rctx.fillStyle = mask; rctx.fillRect(0, 0, size, size);
      ctx.drawImage(ring, 0, 0); releaseCanvas(ring);
      return canvas;
    }
    const sprites = { core: compose(body, 1, 0.78), dark: compose(dark, 0.3, 0.2), halo: halo };
    releaseCanvas(body); releaseCanvas(dark);
    return sprites;
  }

  /* Static deep field: haze, a faint dust band and distant stars (PWA bakeBackdrop). */
  function bakeBackdrop(canvas, L, dots, noise) {
    const ctx = canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const hw = Math.max(24, Math.round(L.w / 5)), hh = Math.max(24, Math.round(L.h / 5));
    const haze = createCanvas(hw, hh), hctx = haze.getContext('2d'), img = hctx.createImageData(hw, hh), d = img.data;
    const lobe = [150 + (ACCENT[0] - 150) * 0.42, 100 + (ACCENT[1] - 100) * 0.42, 250 + (ACCENT[2] - 250) * 0.42];
    for (let j = 0; j < hh; j++) for (let i = 0; i < hw; i++) {
      const x = (i + 0.5) / hw * L.w, y = (j + 0.5) / hh * L.h;
      const dx = (x - L.cx) / L.R, dy = (y - L.cy) / L.R;
      const n = noise.fbm(x * 0.006 + 11.3, y * 0.006 - 4.2, 4), texture = 0.4 + 1.2 * n;
      const bloom = Math.exp(-(dx * dx + dy * dy) / 1.6) * 0.2;
      const lobeA = Math.exp(-(Math.pow((dx - 1.25) / 1.4, 2) + Math.pow((dy + 1.05) / 0.9, 2))) * 0.1 * texture;
      const lobeB = Math.exp(-(Math.pow((dx + 1.5) / 1.3, 2) + Math.pow((dy - 1.0) / 0.85, 2))) * 0.06 * texture;
      const bx = dx - 1.5, by = dy - 1.4, along = bx * 0.78 - by * 0.62, across = bx * 0.62 + by * 0.78;
      const band = Math.exp(-Math.pow(across / 0.32, 2) - Math.pow(along / 1.5, 2)) * 0.075 * texture;
      const total = bloom + lobeA + lobeB + band, idx = (j * hw + i) * 4;
      if (total <= 0.002) continue;
      d[idx] = (118 * bloom + lobe[0] * lobeA + 84 * lobeB + 150 * band) / total;
      d[idx + 1] = (70 * bloom + lobe[1] * lobeA + 70 * lobeB + 128 * band) / total;
      d[idx + 2] = (235 * bloom + lobe[2] * lobeA + 210 * lobeB + 240 * band) / total;
      d[idx + 3] = 255 * clamp01(total);
    }
    hctx.putImageData(img, 0, 0);
    ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(haze, 0, 0, canvas.width, canvas.height);
    releaseCanvas(haze);
    ctx.setTransform(L.dpr, 0, 0, L.dpr, 0, 0);
    const rnd = mulberry32(SEED ^ 0x51ab), ring = L.R * 1.04;
    function star(x, y, r, a, ci) {
      const dx = x - L.cx, dy = y - L.cy;
      if (dx * dx + dy * dy < ring * ring || a < 0.02) return;
      const half = r * DOT_SPREAD;
      ctx.globalAlpha = a;
      ctx.drawImage(dots, ci * DOT_CELL, 0, DOT_CELL, DOT_CELL, x - half, y - half, half * 2, half * 2);
    }
    const field = Math.round(L.w * L.h / 2600);
    for (let i = 0; i < field; i++) {
      const pick = rnd();
      star(rnd() * L.w, rnd() * L.h, 0.4 + 0.75 * Math.pow(rnd(), 2), 0.1 + 0.5 * Math.pow(rnd(), 2.2), pick < 0.35 ? 1 : (pick < 0.65 ? 2 : (pick < 0.9 ? 3 : 0)));
    }
    for (let i = 0; i < 220; i++) {
      const along = gauss(rnd) * 1.3, across = gauss(rnd) * 0.24;
      star(L.cx + (1.5 + along * 0.78 + across * 0.62) * L.R, L.cy + (1.4 - along * 0.62 + across * 0.78) * L.R, 0.35 + 0.5 * rnd(), 0.1 + 0.38 * Math.pow(rnd(), 1.6), rnd() < 0.5 ? 2 : 3);
    }
    ctx.globalAlpha = 1;
  }

  /* ------------------------------------------------------------------ seeding */
  function pickColor(rnd, accentShare) {
    if (rnd() < accentShare) return 5;
    const w = rnd();
    return w < 0.14 ? 0 : (w < 0.36 ? 1 : (w < 0.62 ? 2 : (w < 0.86 ? 3 : 4)));
  }

  function seedOrbit(def, rnd, density) {
    const cfg = def.dust, list = [], clumps = [];
    for (let c = 0; c < (cfg.clumps || 0); c++) clumps.push({ at: rnd() * TAU, width: 0.22 + rnd() * 0.3 });
    const count = Math.round(cfg.count * density);
    for (let i = 0; i < count; i++) {
      const bright = rnd() < 0.1;
      let theta, drift, sway;
      if (def.arc) { theta = gauss(rnd) * def.arc.span * 0.3; drift = 1; sway = 0.05 + rnd() * 0.12; }
      else if (clumps.length && rnd() < 0.42) { const cl = clumps[(rnd() * clumps.length) | 0]; theta = cl.at + gauss(rnd) * cl.width; drift = 1; sway = 0.04 + rnd() * 0.1; }
      else { theta = rnd() * TAU; drift = 0.9 + rnd() * 0.2; sway = rnd() * 0.03; }
      const offset = rnd() < (cfg.halo || 0) ? Math.abs(gauss(rnd)) * cfg.spread * 3.4 : gauss(rnd) * cfg.spread;
      list.push({
        th: theta, sp: drift, rho: 1 + offset, lift: gauss(rnd) * cfg.lift,
        wa: sway, ws: 0.2 + rnd() * 0.5, wp: rnd() * TAU,
        r: (bright ? 1.25 + rnd() * 0.65 : 0.42 + rnd() * 0.74) * cfg.size,
        a: (bright ? 0.76 + rnd() * 0.24 : 0.24 + rnd() * 0.58) * cfg.alpha,
        tw: rnd() * TAU, tws: 0.5 + rnd() * 1.3, ci: bright ? (rnd() < 0.5 ? 0 : 1) : pickColor(rnd, cfg.accent || 0.1),
        fd: rnd() * 0.35
      });
    }
    return list;
  }

  /* Dial matter for the launch: starts on the duration ring, spirals inward under the digits. */
  function seedInflow(rnd, count) {
    const list = [];
    for (let i = 0; i < count; i++) {
      list.push({
        a0: rnd() * TAU, r0: 0.94 + rnd() * 0.16, turn: (0.9 + rnd() * 1.6) * (rnd() < 0.84 ? 1 : -1),
        d: Math.pow(rnd(), 1.3) * 0.5, len: 0.42 + rnd() * 0.2,
        r: 0.8 + rnd() * 1.2, a: 0.55 + rnd() * 0.45, ci: rnd() < 0.55 ? 4 : (rnd() < 0.6 ? 3 : 5), trail: rnd() < 0.55
      });
    }
    return list;
  }

  function seedInner(rnd) {
    const list = [];
    for (let i = 0; i < 44; i++) {
      const y = rnd() * 2 - 1, angle = rnd() * TAU, ring = Math.sqrt(1 - y * y), shell = 0.34 + rnd() * 0.58;
      list.push({ x: ring * Math.cos(angle) * shell, y: y * shell, z: ring * Math.sin(angle) * shell,
        speed: (0.7 + rnd() * 0.6) * (shell < 0.62 ? 1.35 : 0.8) * (rnd() < 0.5 ? 1 : -0.6),
        r: 0.4 + rnd() * 0.6, a: 0.45 + rnd() * 0.55, ci: rnd() < 0.5 ? 1 : 2, tw: rnd() * TAU });
    }
    return list;
  }

  /* ------------------------------------------------------------------ renderer */
  function create(canvas, backdrop, options) {
    options = options || {};
    const ctx = canvas.getContext('2d');
    const rnd = mulberry32(SEED ^ (options.seed || 0));
    const noise = createNoise(mulberry32(SEED ^ 0x9e37));
    const density = options.density || 1;
    const dots = bakeDots();
    const glow = bakeGlow([150, 110, 255]);
    const cloud = bakeCloud(noise);
    const orbits = ORBITS.map(function (def) { return { def: def, dust: def.dust ? seedOrbit(def, rnd, density) : [] }; });
    const inflow = seedInflow(rnd, options.inflowCount || 180);
    const inner = seedInner(rnd);
    const heroes = HERO_STARS.map(function (h) { return { x: h[0], y: h[1], r: h[2], tw: rnd() * TAU, tws: 0.45 + rnd() * 0.85 }; });
    const P = { x: 0, y: 0, z: 0, s: 1 };
    const RX = new Float32Array(ARC_POINTS + 1), RY = new Float32Array(ARC_POINTS + 1), RW = new Float32Array(ARC_POINTS + 1);
    const NX = new Float32Array(ARC_POINTS + 1), NY = new Float32Array(ARC_POINTS + 1);

    const L = { w: 0, h: 0, dpr: 1, cx: 0, cy: 0, R: 100, cam: 560, zone: null, unit: 1 };
    let sprites = null;
    let zoneRel = { cx: 0, cy: 0, rx: 0.72, ry: 0.42 };

    // Every visual state is one of these parameters (scenes tween them on the timeline).
    const fx = {
      core: 0, g: 0, detail: 0, seed: 0, orbit: 0, dust: 0, inflow: 0, bloom: 1, tempo: 1,
      stars: 0, alpha: 1,
      shiftX: 0, shiftY: 0, shiftS: 1 // contraction: offset (stage px) and scale around the core centre
    };

    function layout(opts) {
      L.w = opts.w; L.h = opts.h;
      L.dpr = Math.max(1, Math.min(2, opts.dpr || 1));
      L.cx = opts.cx; L.cy = opts.cy; L.R = opts.R; L.cam = L.R * CAMERA;
      L.unit = Math.max(1, Math.min(2.4, L.R / 112));
      if (opts.zone) {
        const z = opts.zone;
        zoneRel = { cx: ((z.x0 + z.x1) / 2 - L.cx) / L.R, cy: ((z.y0 + z.y1) / 2 - L.cy) / L.R, rx: (z.x1 - z.x0) / 2 / L.R, ry: (z.y1 - z.y0) / 2 / L.R };
        L.zone = { x0: z.x0 - 14, y0: z.y0 - 8, x1: z.x1 + 14, y1: z.y1 + 10 };
      } else L.zone = null;
      canvas.width = Math.round(L.w * L.dpr); canvas.height = Math.round(L.h * L.dpr);
      if (sprites) { releaseCanvas(sprites.core); releaseCanvas(sprites.dark); releaseCanvas(sprites.halo); }
      sprites = bakeCore(L.R, L.dpr, noise, zoneRel);
      if (backdrop) {
        backdrop.width = Math.round(L.w * L.dpr); backdrop.height = Math.round(L.h * L.dpr);
        bakeBackdrop(backdrop, L, dots, noise);
      }
      orbits.forEach(function (o) {
        const def = o.def, roll = def.roll * Math.PI / 180;
        o.a = def.radius * L.R; o.sinI = def.incline; o.cosI = Math.sqrt(1 - def.incline * def.incline);
        o.cosR = Math.cos(roll); o.sinR = Math.sin(roll);
      });
    }

    function zoneMask(x, y) {
      const z = L.zone;
      if (!z) return 1;
      const dx = x < z.x0 ? z.x0 - x : (x > z.x1 ? x - z.x1 : 0);
      const dy = y < z.y0 ? z.y0 - y : (y > z.y1 ? y - z.y1 : 0);
      if (dx === 0 && dy === 0) return 0.05;
      const d = Math.sqrt(dx * dx + dy * dy) / 34;
      return d >= 1 ? 1 : 0.05 + 0.95 * d * d * (3 - 2 * d);
    }

    function project(o, theta, rho, lift) {
      const a = o.a * rho, lx = a * Math.cos(theta), lz = a * Math.sin(theta);
      const y3 = lz * o.sinI * o.def.flip + lift * L.R * o.cosI;
      const z3 = lz * o.cosI - lift * L.R * o.sinI * o.def.flip;
      const s = L.cam / (L.cam - z3), X = lx * s, Y = y3 * s;
      P.x = L.cx + X * o.cosR - Y * o.sinR; P.y = L.cy + X * o.sinR + Y * o.cosR; P.z = z3; P.s = s;
    }

    function dot(ci, x, y, r, alpha) {
      if (alpha < 0.012) return;
      const half = r * DOT_SPREAD;
      ctx.globalAlpha = alpha > 1 ? 1 : alpha;
      ctx.drawImage(dots, ci * DOT_CELL, 0, DOT_CELL, DOT_CELL, x - half, y - half, half + half, half + half);
    }

    function ribbon(n, widen, style, alpha) {
      ctx.globalAlpha = alpha > 1 ? 1 : alpha;
      ctx.fillStyle = style;
      ctx.beginPath();
      for (let j = 0; j <= n; j++) { const w = RW[j] * widen; const x = RX[j] + NX[j] * w, y = RY[j] + NY[j] * w; if (j === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y); }
      for (let j = n; j >= 0; j--) { const w = RW[j] * widen; ctx.lineTo(RX[j] - NX[j] * w, RY[j] - NY[j] * w); }
      ctx.closePath(); ctx.fill();
    }

    function arc(o, a0, a1, front, width, taper, halo, core, alpha, glowLevel) {
      if (a1 - a0 < 0.0005 || alpha < 0.004) return;
      let from = a0;
      while (from < a1 - 0.000001) {
        const half = Math.floor(from / Math.PI + 1e-9), to = Math.min(a1, (half + 1) * Math.PI);
        if ((((half % 2) + 2) % 2 === 0) === front) {
          const n = Math.max(2, Math.min(ARC_POINTS, Math.ceil((to - from) / ARC_STEP)));
          for (let j = 0; j <= n; j++) {
            const theta = from + (to - from) * j / n;
            project(o, theta, 1, 0);
            let w = width * P.s * 0.5;
            if (taper > 0) { const e = Math.min(theta - a0, a1 - theta) / taper; w *= e >= 1 ? 1 : (e <= 0 ? 0 : e * e * (3 - 2 * e)); }
            RX[j] = P.x; RY[j] = P.y; RW[j] = w;
          }
          for (let j = 0; j <= n; j++) {
            const a = j > 0 ? j - 1 : 0, b = j < n ? j + 1 : n;
            const tx = RX[b] - RX[a], ty = RY[b] - RY[a], len = Math.sqrt(tx * tx + ty * ty) || 1;
            NX[j] = -ty / len; NY[j] = tx / len;
          }
          if (glowLevel >= 2) ribbon(n, 7, halo, alpha * 0.085);
          if (glowLevel >= 1) ribbon(n, 3, halo, alpha * 0.27);
          ribbon(n, 1, core, alpha);
        }
        from = to;
      }
    }

    function drawPaths(t, front) {
      for (let n = 0; n < orbits.length; n++) {
        const o = orbits[n], def = o.def, line = def.line;
        if (!line) continue;
        const prog = smooth(def.form.at, def.form.at + def.form.span, fx.orbit);
        const alpha = line.alpha * smooth(0, 0.25, prog) * (front ? 1 : 0.62);
        if (prog <= 0 || alpha < 0.004) continue;
        const halo = rgba(line.halo, 1), core = rgba(front ? line.core : line.halo, 1);
        const turn = def.dir * TAU * t / def.period;
        const width = line.width * L.unit;
        let a0 = 0, a1 = TAU, taper = 0;
        if (def.arc) {
          const half = def.arc.span * 0.5 * prog, centre = def.arc.phase + turn;
          a0 = centre - half; a1 = centre + half; taper = Math.min(def.arc.taper, half * 0.95);
        } else if (prog < 1) {
          taper = 0.06 + 0.5 * (1 - prog);
          if (def.form.from === 'rear') { a0 = Math.PI * 1.5 - prog * Math.PI; a1 = Math.PI * 1.5 + prog * Math.PI; }
          else if (def.dir > 0) { a0 = def.form.start || 0; a1 = a0 + prog * TAU; }
          else { a1 = def.form.start || 0; a0 = a1 - prog * TAU; }
        }
        // Normalise so the front/rear split (by half turns) stays correct for negative angles.
        const shift = Math.ceil(-Math.min(a0, 0) / TAU) * TAU;
        arc(o, a0 + shift, a1 + shift, front, width, taper, halo, core, alpha, line.glow);
        if (def.crests && prog >= 1) {
          for (let c = 0; c < def.crests.length; c++) {
            const cr = def.crests[c];
            let centre = cr.phase + turn;
            centre = ((centre % TAU) + TAU) % TAU + TAU;
            arc(o, centre - cr.span / 2, centre + cr.span / 2, front, width * cr.width, cr.span * 0.46, halo, rgba([255, 250, 255], 1), alpha * cr.gain * 0.9, 1);
          }
        }
      }
    }

    function drawDust(t, front) {
      const fade = fx.dust;
      if (fade < 0.01) return;
      for (let n = 0; n < orbits.length; n++) {
        const o = orbits[n], def = o.def, list = o.dust;
        const prog = smooth(def.form.at, def.form.at + def.form.span + 0.2, fx.orbit);
        if (prog <= 0) continue;
        const speed = def.dir * TAU / def.period;
        for (let i = 0; i < list.length; i++) {
          const p = list[i];
          const emerge = smooth(p.fd, p.fd + 0.65, prog);
          if (emerge <= 0) continue;
          const theta = p.th + speed * p.sp * t + p.wa * Math.sin(p.ws * t + p.wp);
          project(o, theta, mix(0.4, p.rho, easeOutCubic(emerge)), p.lift);
          if ((P.z > 0) !== front) continue;
          let a = p.a * (0.72 + 0.28 * Math.sin(p.tw + p.tws * t)) * fade * emerge;
          if (front) a *= zoneMask(P.x, P.y);
          else a *= 0.75;
          dot(p.ci, P.x, P.y, p.r * P.s * 1.35 * Math.min(1.6, L.unit * 0.75), a);
        }
      }
    }

    function drawInflow() {
      const k = fx.inflow;
      if (k <= 0 || k >= 1) return;
      const Rd = L.R * (options.dialRatio || 1.22);
      ctx.lineCap = 'round';
      for (let i = 0; i < inflow.length; i++) {
        const p = inflow[i];
        const u = clamp01((k - p.d) / p.len);
        if (u <= 0 || u >= 1) continue;
        const e = easeInCubic(u);
        const rr = mix(Rd * p.r0, L.R * 0.04, e), ang = p.a0 + p.turn * e;
        const x = L.cx + Math.cos(ang) * rr, y = L.cy + Math.sin(ang) * rr * 0.96;
        const a = p.a * smooth(0, 0.12, u) * (1 - smooth(0.82, 1, u));
        if (p.trail && u > 0.05) {
          const e2 = easeInCubic(Math.max(0, u - 0.07));
          const r2 = mix(Rd * p.r0, L.R * 0.04, e2), a2 = p.a0 + p.turn * e2;
          ctx.globalAlpha = a * 0.4;
          ctx.strokeStyle = rgba(PALETTE[p.ci], 1);
          ctx.lineWidth = p.r * 1.1;
          ctx.beginPath(); ctx.moveTo(L.cx + Math.cos(a2) * r2, L.cy + Math.sin(a2) * r2 * 0.96); ctx.lineTo(x, y); ctx.stroke();
        }
        dot(p.ci, x, y, p.r * 1.4, a);
      }
    }

    function drawCore(t) {
      const g = fx.g;
      if (fx.seed > 0.004) {
        const size = L.R * (0.28 + 0.5 * fx.seed) + 30;
        ctx.globalAlpha = Math.min(1, 0.9 * fx.seed);
        ctx.drawImage(glow, L.cx - size, L.cy - size, size * 2, size * 2);
      }
      if (g <= 0.001 || fx.core <= 0.002) return;
      const R = L.R, cx = L.cx, cy = L.cy;
      const breathe = 0.5 + 0.5 * Math.sin(t * 0.62);
      const bloom = fx.core * fx.bloom * (0.12 + 0.07 * breathe);
      if (bloom > 0.004) { const size = R * (0.7 + 1.15 * g); ctx.globalAlpha = Math.min(1, bloom); ctx.drawImage(glow, cx - size, cy - size, size * 2, size * 2); }
      const half = R * CORE_SPAN * g;
      if (fx.detail < 0.999) { ctx.globalAlpha = fx.core; ctx.drawImage(sprites.dark, cx - half, cy - half, half * 2, half * 2); }
      if (fx.detail > 0.004) { ctx.globalAlpha = fx.core * fx.detail; ctx.drawImage(sprites.core, cx - half, cy - half, half * 2, half * 2); }
      const life = fx.core * fx.detail;
      if (life > 0.02 && g > 0.3) {
        const rad = R * g;
        ctx.save();
        ctx.beginPath(); ctx.arc(cx, cy, rad * 0.985, 0, TAU); ctx.clip();
        ctx.globalCompositeOperation = 'lighter';
        const a = rad * 0.66, b = rad * 0.54;
        const ax = cx + rad * (-0.4 + 0.1 * Math.sin(t / 23)), ay = cy + rad * (-0.56 + 0.06 * Math.cos(t / 31));
        const bx = cx + rad * (0.12 + 0.12 * Math.cos(t / 29)), by = cy + rad * (-0.7 + 0.05 * Math.sin(t / 19));
        ctx.globalAlpha = life * (0.2 + 0.05 * breathe); ctx.drawImage(cloud, ax - a, ay - a, a * 2, a * 2);
        ctx.globalAlpha = life * 0.15; ctx.drawImage(cloud, bx - b, by - b, b * 2, b * 2);
        for (let i = 0; i < inner.length; i++) {
          const s = inner[i], ang = t * 0.06 * s.speed, c = Math.cos(ang), sn = Math.sin(ang);
          const x = s.x * c - s.z * sn, z = s.x * sn + s.z * c;
          const px = cx + x * rad, py = cy + s.y * rad;
          const fade = (0.35 + 0.65 * (z + 1) / 2) * (0.7 + 0.3 * Math.sin(s.tw + t * 0.9));
          dot(s.ci, px, py, s.r * L.unit * 0.8, s.a * life * fade * 0.5 * zoneMask(px, py));
        }
        ctx.restore();
      }
    }

    function drawHeroes(t) {
      if (fx.stars <= 0.01) return;
      for (let i = 0; i < heroes.length; i++) {
        const h = heroes[i];
        const x = L.cx + h.x * L.R, y = L.cy + h.y * L.R;
        if (x < 10 || x > L.w - 10 || y < 10 || y > L.h - 10) continue;
        const a = fx.stars * (0.62 + 0.3 * Math.sin(h.tw + h.tws * t));
        const halo = h.r * L.unit * 5;
        ctx.globalAlpha = Math.min(1, a * 0.4);
        ctx.drawImage(glow, x - halo, y - halo, halo * 2, halo * 2);
        dot(1, x, y, h.r * L.unit * 0.8, a);
      }
    }

    /* Draw one frame at play time tSec (seconds). */
    function render(tSec) {
      const t = tSec * fx.tempo;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      // The master fade is the canvas opacity (per-draw globalAlpha stays exact underneath).
      const opacity = fx.alpha >= 0.999 ? '' : String(Math.max(0, fx.alpha).toFixed(3));
      if (canvas.style.opacity !== opacity) canvas.style.opacity = opacity;
      if (fx.alpha <= 0.002) return;
      // Contraction: scale and move the whole system around the core centre.
      const s = fx.shiftS;
      const ox = L.cx + fx.shiftX, oy = L.cy + fx.shiftY;
      ctx.setTransform(L.dpr * s, 0, 0, L.dpr * s, L.dpr * (ox - L.cx * s), L.dpr * (oy - L.cy * s));
      ctx.globalCompositeOperation = 'source-over';
      drawHeroes(t);
      drawPaths(t, false);
      drawDust(t, false);
      drawCore(t);
      drawInflow();
      drawPaths(t, true);
      drawDust(t, true);
      ctx.globalAlpha = 1;
    }

    function set(values) { for (const k in values) if (Object.prototype.hasOwnProperty.call(fx, k)) fx[k] = values[k]; }
    function get(k) { return fx[k]; }

    function dispose() {
      if (sprites) { releaseCanvas(sprites.core); releaseCanvas(sprites.dark); releaseCanvas(sprites.halo); sprites = null; }
      releaseCanvas(dots); releaseCanvas(glow); releaseCanvas(cloud);
    }

    return { layout: layout, render: render, set: set, get: get, fx: fx, dispose: dispose, L: L };
  }

  Pitch.FocusCore = Object.freeze({ create: create });
})(window);
