/**
 * Physical module: src/core/cosmic-core.js
 *
 * v17.9 Cosmic Core — the single renderer of the Focus Session cosmic scene (#ucScene).
 *
 * Ownership
 * - One renderer, one particle system and one requestAnimationFrame loop for Quick Focus and
 *   Mission Focus. navigation.js starts and stops it together with the timer screen.
 * - The entrance of the cosmic region (birth for a new Session, calm re-entry for a restored one)
 *   is a timeline inside this loop. MotionController keeps owning the screen crossfade.
 * - The timer, Fokus-Schutz and every other control stay real DOM above the canvas and are never
 *   touched here. Their text boxes are only measured to keep a readability zone clear.
 *
 * Scene model
 * - #ucCore is an invisible geometry anchor: CSS decides where the core sits and how large it is.
 * - Everything is positioned in a small 3D space around that core and projected with a weak
 *   perspective. Whatever lies behind the core is painted first, then the core, then whatever is
 *   in front, so occlusion is exact without masks.
 * - #ucCosmicBackdrop holds the static deep field (haze, faint stars), painted once per layout.
 *   #ucCosmicCanvas holds everything that moves.
 * - All variation is seeded, and all motion is a closed-form function of one clock. Nothing is
 *   simulated step by step, so pausing, resuming and quality changes never rebuild the scene.
 */
(function installCosmicCoreV179(global) {
  'use strict';

  const TAU = Math.PI * 2;
  const CAMERA = 5.6; // Camera distance in core radii.
  const CORE_SPAN = 1.55; // Half extent of the core sprite in core radii (body plus atmosphere).
  const DOT_CELL = 32;
  const DOT_SPREAD = 2.9; // Sprite half size per unit of particle radius.
  const ARC_STEP = TAU / 96;
  const ARC_POINTS = 112;
  const ZONE_FEATHER = 24;
  const ZONE_FLOOR = 0.05;
  const BIRTH_MS = 2150;
  const REENTRY_MS = 950;
  const STATIC_CLOCK = 7.4; // Frozen moment shown with reduced motion.
  const RELEASE_DELAY_MS = 1200;
  const SEED = 0x17c09e;

  // Warm white, cool white, pale violet, lavender, restrained purple, accent.
  const PALETTE = [
    [255, 244, 232],
    [238, 240, 255],
    [224, 214, 255],
    [196, 181, 253],
    [167, 139, 250],
    [158, 198, 246]
  ];
  const ACCENT = 5;
  // Same base tones as the Home Mission aura (multi-mission-ui.js).
  const THEME_TONES = {
    aubergine: [109, 40, 217],
    forest: [21, 128, 92],
    ember: [194, 92, 46],
    burgundy: [168, 42, 82]
  };

  const TIERS = {
    high: { name: 'high', dprCap: 2, density: 1, sparks: 1, inner: 44, clouds: true, trails: true },
    medium: { name: 'medium', dprCap: 2, density: 0.6, sparks: 0.55, inner: 28, clouds: true, trails: true },
    low: { name: 'low', dprCap: 1.5, density: 0.32, sparks: 0.25, inner: 14, clouds: false, trails: false }
  };
  const TIER_ORDER = ['high', 'medium', 'low'];

  /* Orbit system. Radii are in core radii, roll in degrees (clockwise on screen), incline is the
     minor/major ratio before perspective. flip -1 means the near side passes above the core. */
  const ORBITS = [
    {
      id: 'primary', radius: 1.56, minRadius: 1.28, incline: 0.3, roll: 7, flip: 1, period: 30, dir: 1,
      line: { width: 2.2, alpha: 0.95, glow: 2, core: [240, 232, 255], halo: [150, 108, 255] },
      crests: [{ phase: 0.9, span: 1.5, width: 1.45, gain: 0.9 }, { phase: 3.9, span: 0.9, width: 1.2, gain: 0.45 }],
      form: { at: 700, ms: 660, from: 'rear' },
      dust: { count: 210, spread: 0.06, lift: 0.03, clumps: 3, halo: 0.36, size: 1, alpha: 1 },
      clearZone: true
    },
    {
      id: 'inclined', radius: 1.36, minRadius: 1.1, incline: 0.6, roll: -56, flip: 1, period: 19, dir: -1,
      line: { width: 1, alpha: 0.5, glow: 1, core: [214, 198, 255], halo: [139, 92, 246] },
      crests: [{ phase: 2.2, span: 1.1, width: 1.3, gain: 0.8 }],
      form: { at: 790, ms: 640, from: 'sweep', start: 4.3 },
      dust: { count: 84, spread: 0.045, lift: 0.016, clumps: 2, halo: 0.2, size: 0.88, alpha: 0.9 }
    },
    {
      id: 'steep', radius: 1.5, minRadius: 1.1, incline: 0.5, roll: 63, flip: 1, period: 15, dir: 1,
      line: { width: 0.8, alpha: 0.34, glow: 1, core: [198, 184, 253], halo: [124, 92, 246] },
      crests: [{ phase: 5.1, span: 0.9, width: 1.3, gain: 0.8 }],
      form: { at: 880, ms: 620, from: 'sweep', start: 4.9 },
      dust: { count: 58, spread: 0.04, lift: 0.014, clumps: 1, halo: 0.2, size: 0.82, alpha: 0.78 }
    },
    {
      id: 'arc', radius: 1.5, minRadius: 1.14, incline: 0.42, roll: -22, flip: -1, period: 36, dir: -1,
      line: { width: 1.15, alpha: 0.5, glow: 1, core: [226, 216, 255], halo: [150, 120, 250], accent: 0.6, rear: 0.34 },
      arc: { phase: 5.6, span: 2.5, taper: 1 },
      form: { at: 960, ms: 620 },
      dust: { count: 96, spread: 0.11, lift: 0.05, size: 0.95, alpha: 1, accent: 0.5 }
    },
    {
      id: 'stream', radius: 1.12, minRadius: 1.06, incline: 0.5, roll: -5, flip: -1, period: 11, dir: 1,
      form: { at: 900, ms: 600 },
      dust: { count: 36, spread: 0.028, lift: 0.01, clumps: 2, size: 0.7, alpha: 0.82 }
    }
  ];

  // Bright anchor stars, in core radii from the core centre. Ones outside the scene are dropped.
  const HERO_STARS = [
    [0.42, -1.62, 1.5], [1.38, -1.52, 1.7], [-1.42, -1.04, 1.6], [1.5, -0.6, 1.85], [-1.5, 0.72, 1.9],
    [1.34, 1.26, 1.55], [-0.66, 1.52, 1.35], [-1.16, -1.74, 1.25], [2.3, -0.92, 1.5], [-2.36, 0.3, 1.4],
    [2.2, 1.04, 1.3], [-2.1, -1.3, 1.45]
  ];

  const DOT_STYLES = PALETTE.map(function () { return ''; });
  const P = { x: 0, y: 0, z: 0, s: 1 }; // Reused projection result.
  const RIBBON = {
    x: new Float32Array(ARC_POINTS + 1),
    y: new Float32Array(ARC_POINTS + 1),
    w: new Float32Array(ARC_POINTS + 1),
    nx: new Float32Array(ARC_POINTS + 1),
    ny: new Float32Array(ARC_POINTS + 1)
  };

  let st = null; // Live scene state; null while the timer screen is not shown.
  let rafId = 0;
  let releaseTimer = null;

  /* ------------------------------------------------------------------ helpers */
  function clamp01(v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
  function smooth(e0, e1, v) {
    const k = clamp01((v - e0) / (e1 - e0));
    return k * k * (3 - 2 * k);
  }
  function easeOutCubic(k) { const m = 1 - k; return 1 - m * m * m; }
  function easeOutQuint(k) { const m = 1 - k; return 1 - m * m * m * m * m; }
  function easeInOutCubic(k) { return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2; }
  function mix(a, b, k) {
    return [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
  }
  function rgba(c, a) {
    return 'rgba(' + Math.round(c[0]) + ',' + Math.round(c[1]) + ',' + Math.round(c[2]) + ',' + clamp01(a).toFixed(3) + ')';
  }
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
    const perm = new Uint8Array(512);
    const val = new Float32Array(256);
    for (let i = 0; i < 256; i++) { perm[i] = i; val[i] = rnd(); }
    for (let i = 255; i > 0; i--) {
      const j = (rnd() * (i + 1)) | 0;
      const t = perm[i]; perm[i] = perm[j]; perm[j] = t;
    }
    for (let i = 0; i < 256; i++) perm[256 + i] = perm[i];
    function at(x, y) {
      const xi = Math.floor(x), yi = Math.floor(y);
      const xf = x - xi, yf = y - yi;
      const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
      const X = xi & 255, Y = yi & 255;
      const a = val[perm[X + perm[Y]]], b = val[perm[X + 1 + perm[Y]]];
      const c = val[perm[X + perm[Y + 1]]], d = val[perm[X + 1 + perm[Y + 1]]];
      return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }
    return {
      fbm: function (x, y, octaves) {
        let sum = 0, amp = 0.5, norm = 0;
        for (let o = 0; o < octaves; o++) {
          sum += amp * at(x, y);
          norm += amp;
          x = x * 2.03 + 17.1;
          y = y * 2.03 - 9.7;
          amp *= 0.5;
        }
        return sum / norm;
      }
    };
  }
  function createCanvas(w, h) {
    const c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(w));
    c.height = Math.max(1, Math.round(h));
    return c;
  }
  function releaseCanvas(c) { if (c) { c.width = 0; c.height = 0; } }
  function prefersReducedMotion() {
    try { return Boolean(global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches); } catch (_) { return false; }
  }
  function textBox(el) {
    try {
      const range = document.createRange();
      range.selectNodeContents(el);
      const box = range.getBoundingClientRect();
      if (box.width > 0 && box.height > 0) return box;
    } catch (_) {}
    return el.getBoundingClientRect();
  }

  /* ------------------------------------------------------------------ layout */
  function pickTier() {
    const memory = Number(global.navigator && global.navigator.deviceMemory) || 0;
    if (memory && memory <= 2) return 'low';
    if (memory && memory <= 4) return 'medium';
    return 'high';
  }

  function measureZone(scene, rect, cx, cy, R) {
    const digits = scene.querySelector('#timerDigits');
    const caption = scene.querySelector('#timerCaption');
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    [digits, caption].forEach(function (el) {
      if (!el) return;
      const box = textBox(el);
      if (!(box.width > 0)) return;
      x0 = Math.min(x0, box.left - rect.left);
      x1 = Math.max(x1, box.right - rect.left);
      y0 = Math.min(y0, box.top - rect.top);
      y1 = Math.max(y1, box.bottom - rect.top);
    });
    if (!(x1 > x0)) {
      x0 = cx - R * 0.72; x1 = cx + R * 0.72; y0 = cy - R * 0.44; y1 = cy + R * 0.4;
    }
    return { x0: x0 - 9, y0: y0 - 5, x1: x1 + 9, y1: y1 + 6 };
  }

  function measure(scene, tier) {
    const rect = scene.getBoundingClientRect();
    if (!(rect.width > 1 && rect.height > 1)) return null;
    const w = rect.width, h = rect.height;
    const anchor = scene.querySelector('#ucCore');
    const box = anchor ? anchor.getBoundingClientRect() : null;
    const hasBox = Boolean(box && box.width > 1);
    const R = hasBox ? box.width / 2 : Math.min(w * 0.3, 112);
    const cx = hasBox ? box.left + box.width / 2 - rect.left : w / 2;
    const cy = hasBox ? box.top + box.height / 2 - rect.top : h * 0.48;
    const protect = scene.querySelector('.uc-protect');
    const protectBox = protect ? protect.getBoundingClientRect() : null;
    const protectTop = protectBox && protectBox.width > 0 ? protectBox.top - rect.top : h;
    const budget = Math.sqrt(1700000 / (w * h)); // Keeps the canvas near 1.7 MP on wide layouts.
    const dpr = Math.max(1, Math.min(global.devicePixelRatio || 1, tier.dprCap, budget));
    return {
      w: w, h: h, dpr: dpr, cx: cx, cy: cy, R: R, cam: R * CAMERA,
      sx: Math.round(w * dpr) / w, sy: Math.round(h * dpr) / h,
      zone: measureZone(scene, rect, cx, cy, R),
      floor: Math.min(h - 8, Math.max(protectTop - 6, cy + R * 1.12))
    };
  }

  // 0 inside the timer readability zone, 1 away from it.
  function zoneMask(L, x, y) {
    const z = L.zone;
    const dx = x < z.x0 ? z.x0 - x : (x > z.x1 ? x - z.x1 : 0);
    const dy = y < z.y0 ? z.y0 - y : (y > z.y1 ? y - z.y1 : 0);
    if (dx === 0 && dy === 0) return ZONE_FLOOR;
    const d = Math.sqrt(dx * dx + dy * dy) / ZONE_FEATHER;
    if (d >= 1) return 1;
    return ZONE_FLOOR + (1 - ZONE_FLOOR) * d * d * (3 - 2 * d);
  }
  function edgeFade(L, x, y, range) {
    const d = Math.min(x, L.w - x, y, L.h - y) / range;
    return d <= 0 ? 0 : (d >= 1 ? 1 : d * d * (3 - 2 * d));
  }

  /* ------------------------------------------------------------------ orbit geometry */
  function projectOrbit(o, L, theta, rho, lift) {
    const a = o.a * rho;
    const lx = a * Math.cos(theta);
    const lz = a * Math.sin(theta);
    const y3 = lz * o.sinI * o.flip + lift * o.cosI;
    const z3 = lz * o.cosI - lift * o.sinI * o.flip;
    const s = L.cam / (L.cam - z3);
    const X = lx * s, Y = y3 * s;
    P.x = L.cx + X * o.cosR - Y * o.sinR;
    P.y = L.cy + X * o.sinR + Y * o.cosR;
    P.z = z3;
    P.s = s;
  }

  function shapeOrbit(o, L, radius, incline, rollDeg) {
    const roll = rollDeg * Math.PI / 180;
    o.a = radius * L.R;
    o.sinI = incline;
    o.cosI = Math.sqrt(1 - incline * incline);
    o.cosR = Math.cos(roll);
    o.sinR = Math.sin(roll);
  }

  // Fits one orbit inside the scene and, for the primary ring, clear of the timer caption.
  function fitOrbit(o, L) {
    const def = o.def;
    let radius = def.radius, incline = def.incline, roll = def.roll;
    for (let pass = 0; pass < 48; pass++) {
      shapeOrbit(o, L, radius, incline, roll);
      let overflow = 0, blocked = false;
      for (let i = 0; i < 72; i++) {
        const theta = i * TAU / 72;
        projectOrbit(o, L, theta, 1, 0);
        overflow = Math.max(overflow, 8 - P.x, P.x - (L.w - 8), 8 - P.y, P.y - L.floor);
        if (def.clearZone && P.z > 0 && zoneMask(L, P.x, P.y) < 0.36) blocked = true;
      }
      if (overflow <= 0 && !blocked) break;
      if (blocked && incline < 0.46) { incline += 0.02; continue; }
      if (blocked && Math.abs(roll) > 1) { roll *= 0.8; continue; }
      if (overflow <= 0) break;
      if (radius > def.minRadius + 0.001) radius = Math.max(def.minRadius, radius - 0.03);
      else if (Math.abs(roll) > 2) roll *= 0.88;
      else break;
    }
  }

  function arcGradient(ctx, o, L, front, color, tipAlpha, midAlpha, exponent) {
    const ext = o.a * 1.16;
    const gx = L.cx - o.cosR * ext, gy = L.cy - o.sinR * ext;
    const grad = ctx.createLinearGradient(gx, gy, L.cx + o.cosR * ext, L.cy + o.sinR * ext);
    // Stops follow the branch on which the screen position is monotonic along the major axis.
    let first = front ? 0 : Math.PI, best = -Infinity;
    if (front) {
      for (let i = 0; i <= 30; i++) {
        const theta = i * Math.PI / 60;
        projectOrbit(o, L, theta, 1, 0);
        const along = (P.x - L.cx) * o.cosR + (P.y - L.cy) * o.sinR;
        if (along > best) { best = along; first = theta; }
      }
    }
    const last = front ? Math.PI - first : TAU;
    grad.addColorStop(0, rgba(color, tipAlpha));
    grad.addColorStop(1, rgba(color, tipAlpha));
    for (let i = 0; i <= 20; i++) {
      const theta = first + (last - first) * i / 20;
      projectOrbit(o, L, theta, 1, 0);
      const along = (P.x - L.cx) * o.cosR + (P.y - L.cy) * o.sinR;
      const k = Math.min(1, Math.abs(along) / o.a);
      let alpha = tipAlpha + (midAlpha - tipAlpha) * Math.pow(1 - k * k, exponent);
      if (front) alpha *= zoneMask(L, P.x, P.y);
      grad.addColorStop(clamp01((along + ext) / (2 * ext)), rgba(color, alpha));
    }
    return grad;
  }

  function paintOrbit(ctx, o, L, accent) {
    const line = o.def.line;
    if (!line) { o.paint = null; return; }
    const core = line.accent ? mix(line.core, accent, line.accent) : line.core;
    const halo = line.accent ? mix(line.halo, accent, line.accent) : line.halo;
    o.paint = {
      coreF: arcGradient(ctx, o, L, true, core, 0.34, 1, 0.42),
      coreR: arcGradient(ctx, o, L, false, core, 0.34, line.rear || 0.15, 0.36),
      haloF: arcGradient(ctx, o, L, true, halo, 0.34, 1, 0.42),
      haloR: arcGradient(ctx, o, L, false, halo, 0.34, line.rear || 0.15, 0.36),
      crestF: arcGradient(ctx, o, L, true, [255, 250, 255], 0.26, 1, 0.5),
      crestR: arcGradient(ctx, o, L, false, [255, 250, 255], 0.26, 0.08, 0.36)
    };
  }

  /* ------------------------------------------------------------------ baked sprites */
  function bakeDots(palette) {
    const canvas = createCanvas(DOT_CELL * palette.length, DOT_CELL);
    const ctx = canvas.getContext('2d');
    const half = DOT_CELL / 2;
    for (let i = 0; i < palette.length; i++) {
      const x = i * DOT_CELL + half;
      const grad = ctx.createRadialGradient(x, half, 0, x, half, half);
      grad.addColorStop(0, rgba(palette[i], 1));
      grad.addColorStop(0.26, rgba(palette[i], 0.94));
      grad.addColorStop(0.42, rgba(palette[i], 0.3));
      grad.addColorStop(0.68, rgba(palette[i], 0.07));
      grad.addColorStop(1, rgba(palette[i], 0));
      ctx.fillStyle = grad;
      ctx.fillRect(i * DOT_CELL, 0, DOT_CELL, DOT_CELL);
    }
    return canvas;
  }

  function bakeGlow(color) {
    const size = 128, half = size / 2;
    const canvas = createCanvas(size, size);
    const ctx = canvas.getContext('2d');
    const grad = ctx.createRadialGradient(half, half, 0, half, half, half);
    grad.addColorStop(0, rgba(color, 0.6));
    grad.addColorStop(0.22, rgba(color, 0.3));
    grad.addColorStop(0.5, rgba(color, 0.09));
    grad.addColorStop(1, rgba(color, 0));
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, size, size);
    return canvas;
  }

  function bakeCloud(noise, color) {
    const size = 96;
    const canvas = createCanvas(size, size);
    const ctx = canvas.getContext('2d');
    const img = ctx.createImageData(size, size);
    const d = img.data;
    for (let j = 0; j < size; j++) {
      for (let i = 0; i < size; i++) {
        const u = (i + 0.5) / size * 2 - 1, v = (j + 0.5) / size * 2 - 1;
        const fall = 1 - smooth(0.15, 1, Math.sqrt(u * u + v * v));
        const n = noise.fbm(u * 2.1 + 40.5, v * 2.1 + 8.3, 4);
        const idx = (j * size + i) * 4;
        d[idx] = color[0]; d[idx + 1] = color[1]; d[idx + 2] = color[2];
        d[idx + 3] = 255 * fall * smooth(0.3, 0.78, n);
      }
    }
    ctx.putImageData(img, 0, 0);
    return canvas;
  }

  /* The core: a per-pixel shaded sphere (rim light from the upper left and slightly behind,
     nebula mapped onto the sphere, a dark centre where the timer sits) plus its atmosphere.
     Two variants share one pass: the full core and the dark seed body it grows out of. */
  function bakeCore(L, noise, accent) {
    const R = L.R, dpr = L.dpr;
    const size = Math.ceil(R * CORE_SPAN * 2 * dpr);
    const q = Math.max(128, Math.min(288, Math.round(R * 2 * dpr * 0.72)));
    const body = createCanvas(q, q), bodyCtx = body.getContext('2d');
    const dark = createCanvas(q, q), darkCtx = dark.getContext('2d');
    const bodyImg = bodyCtx.createImageData(q, q), darkImg = darkCtx.createImageData(q, q);
    const bd = bodyImg.data, dd = darkImg.data;
    const z = L.zone;
    const zcx = ((z.x0 + z.x1) / 2 - L.cx) / R, zcy = ((z.y0 + z.y1) / 2 - L.cy) / R;
    const zrx = Math.max(0.5, (z.x1 - z.x0) / 2 / R), zry = Math.max(0.34, (z.y1 - z.y0) / 2 / R * 1.1);
    const primary = ORBITS[0];
    // Half vector of the specular glint: upper left, above the digits.
    const hx = -0.3, hy = -0.66, hz = Math.sqrt(1 - hx * hx - hy * hy);
    for (let j = 0; j < q; j++) {
      const v0 = (j + 0.5) / q * 2 - 1;
      for (let i = 0; i < q; i++) {
        const u0 = (i + 0.5) / q * 2 - 1;
        const r = Math.sqrt(u0 * u0 + v0 * v0);
        const idx = (j * q + i) * 4;
        if (r > 1.06) continue;
        // Pixels just outside the limb repeat the limb so the clipped edge has no dark fringe.
        const u = r > 1 ? u0 / r : u0, v = r > 1 ? v0 / r : v0;
        const rc = r > 1 ? 1 : r;
        const nz = Math.sqrt(Math.max(0, 1 - rc * rc));
        const ndl = -0.52 * u - 0.85 * v - 0.06 * nz;
        const lit = smooth(-0.35, 0.85, ndl);
        const rimLit = smooth(-0.2, 0.7, ndl);
        const fres = Math.pow(1 - nz, 2.4);
        const k = rc > 0.0001 ? Math.asin(rc) / rc : 1;
        const tu = u * k, tv = v * k;
        const n1 = noise.fbm(tu * 1.5 + 3.1, tv * 1.5 - 1.4, 4);
        const n2 = noise.fbm(tu * 3.4 - 7.7 + n1 * 0.9, tv * 3.4 + 2.2 - n1 * 0.6, 3);
        const cloud = smooth(0.36, 0.8, n1 * 0.62 + n2 * 0.38);
        const ex = (u - zcx) / zrx, ey = (v - zcy) / zry;
        const hollow = 1 - smooth(0.62, 1.4, Math.sqrt(ex * ex + ey * ey));
        const depth = 0.5 + 0.5 * Math.pow(nz, 0.6);
        const nb = cloud * (0.26 + 0.74 * lit) * (1 - 0.88 * hollow) * depth;
        const hot = nb * cloud * lit;
        const rim = fres * (0.12 + 0.88 * rimLit);
        const edge = fres * fres * rimLit;
        const counter = fres * smooth(0.15, 0.9, -ndl) * 0.3;
        const lens = Math.exp(-Math.pow((rc - 0.87) / 0.014, 2)) * (0.05 + 0.2 * lit);
        // Light of the primary ring on the lower face, where the ring passes in front.
        const ringY = primary.incline * primary.radius * 1.28 * Math.sqrt(Math.max(0, 1 - Math.pow(u / 1.7, 2))) + 0.12 * u;
        const sheen = Math.exp(-Math.pow((v - ringY + 0.05) / 0.13, 2)) * 0.2 * Math.pow(nz, 0.4);
        const nh = Math.max(0, u * hx + v * hy + nz * hz);
        const glint = Math.pow(nh, 620) + Math.pow(nh, 40) * 0.17;
        const baseR = 5 + 24 * lit * depth, baseG = 4 + 13 * lit * depth, baseB = 15 + 64 * lit * depth;
        bd[idx] = Math.min(255, baseR + 108 * nb + 96 * hot + 150 * rim + 96 * edge + 62 * counter + 170 * lens + accent[0] * sheen * 0.5 + 62 * sheen + 255 * glint);
        bd[idx + 1] = Math.min(255, baseG + 64 * nb + 84 * hot + 116 * rim + 110 * edge + 24 * counter + 150 * lens + accent[1] * sheen * 0.5 + 38 * sheen + 250 * glint);
        bd[idx + 2] = Math.min(255, baseB + 226 * nb + 30 * hot + 250 * rim + 20 * edge + 112 * counter + 255 * lens + accent[2] * sheen * 0.5 + 118 * sheen + 255 * glint);
        bd[idx + 3] = 255;
        dd[idx] = Math.min(255, baseR * 0.55 + 50 * rim);
        dd[idx + 1] = Math.min(255, baseG * 0.55 + 36 * rim);
        dd[idx + 2] = Math.min(255, baseB * 0.6 + 96 * rim);
        dd[idx + 3] = 255;
      }
    }
    bodyCtx.putImageData(bodyImg, 0, 0);
    darkCtx.putImageData(darkImg, 0, 0);

    // Atmosphere outside the limb, brighter towards the light.
    const hq = 144;
    const halo = createCanvas(hq, hq), haloCtx = halo.getContext('2d');
    const haloImg = haloCtx.createImageData(hq, hq), hd = haloImg.data;
    for (let j = 0; j < hq; j++) {
      const v = ((j + 0.5) / hq * 2 - 1) * CORE_SPAN;
      for (let i = 0; i < hq; i++) {
        const u = ((i + 0.5) / hq * 2 - 1) * CORE_SPAN;
        const r = Math.sqrt(u * u + v * v) || 0.0001;
        const d = Math.max(0, r - 1);
        const toward = smooth(-0.5, 0.9, (-0.52 * u - 0.85 * v) / r);
        let a = Math.exp(-d / 0.05) * (0.08 + 0.6 * toward) + Math.exp(-d / 0.2) * (0.03 + 0.13 * toward) + Math.exp(-d / 0.46) * 0.028;
        a *= smooth(CORE_SPAN, CORE_SPAN - 0.3, r) * smooth(0.86, 1, r);
        const idx = (j * hq + i) * 4;
        hd[idx] = 152 + 40 * toward; hd[idx + 1] = 114 + 44 * toward; hd[idx + 2] = 255;
        hd[idx + 3] = 255 * clamp01(a);
      }
    }
    haloCtx.putImageData(haloImg, 0, 0);

    function compose(interior, haloAlpha, rimAlpha) {
      const canvas = createCanvas(size, size);
      const ctx = canvas.getContext('2d');
      const c = size / 2, rad = R * dpr;
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.globalAlpha = haloAlpha;
      ctx.drawImage(halo, 0, 0, size, size);
      ctx.globalAlpha = 1;
      ctx.save();
      ctx.beginPath();
      ctx.arc(c, c, rad, 0, TAU);
      ctx.clip();
      ctx.drawImage(interior, c - rad * 1.0, c - rad * 1.0, rad * 2, rad * 2);
      ctx.restore();
      // Crisp rim light on the lit limb: a full ring masked along the light direction.
      const ring = createCanvas(size, size), rctx = ring.getContext('2d');
      rctx.strokeStyle = rgba([238, 228, 255], rimAlpha);
      rctx.lineWidth = 1.15 * dpr;
      rctx.beginPath();
      rctx.arc(c, c, rad - 0.6 * dpr, 0, TAU);
      rctx.stroke();
      const mask = rctx.createLinearGradient(c - 0.52 * rad, c - 0.85 * rad, c + 0.16 * rad, c + 0.26 * rad);
      mask.addColorStop(0, 'rgba(0,0,0,1)');
      mask.addColorStop(0.45, 'rgba(0,0,0,0.5)');
      mask.addColorStop(1, 'rgba(0,0,0,0)');
      rctx.globalCompositeOperation = 'destination-in';
      rctx.fillStyle = mask;
      rctx.fillRect(0, 0, size, size);
      ctx.drawImage(ring, 0, 0);
      releaseCanvas(ring);
      return canvas;
    }
    const sprites = { core: compose(body, 1, 0.78), dark: compose(dark, 0.3, 0.2) };
    releaseCanvas(body); releaseCanvas(dark); releaseCanvas(halo);
    return sprites;
  }

  // Static deep field: haze with soft structure, a faint dust band and distant stars.
  function bakeBackdrop(canvas, L, dots, noise, accent) {
    const ctx = canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const hw = Math.max(24, Math.round(L.w / 4)), hh = Math.max(24, Math.round(L.h / 4));
    const haze = createCanvas(hw, hh), hctx = haze.getContext('2d');
    const img = hctx.createImageData(hw, hh), d = img.data;
    const lobeColor = mix([150, 100, 250], accent, 0.42);
    for (let j = 0; j < hh; j++) {
      for (let i = 0; i < hw; i++) {
        const x = (i + 0.5) / hw * L.w, y = (j + 0.5) / hh * L.h;
        const dx = (x - L.cx) / L.R, dy = (y - L.cy) / L.R;
        const n = noise.fbm(x * 0.011 + 11.3, y * 0.011 - 4.2, 4);
        const texture = 0.4 + 1.2 * n;
        const bloom = Math.exp(-(dx * dx + dy * dy) / 1.3) * 0.2;
        const lobeA = Math.exp(-(Math.pow((dx - 1.05) / 1.15, 2) + Math.pow((dy + 0.95) / 0.8, 2))) * 0.1 * texture;
        const lobeB = Math.exp(-(Math.pow((dx + 1.2) / 1.0, 2) + Math.pow((dy - 1.0) / 0.75, 2))) * 0.06 * texture;
        // Dust band, lower right towards the right edge.
        const bx = dx - 1.2, by = dy - 1.3;
        const along = bx * 0.78 - by * 0.62, across = bx * 0.62 + by * 0.78;
        const band = Math.exp(-Math.pow(across / 0.26, 2) - Math.pow(along / 1.1, 2)) * 0.075 * texture;
        const total = bloom + lobeA + lobeB + band;
        const idx = (j * hw + i) * 4;
        if (total <= 0.002) continue;
        d[idx] = (118 * bloom + lobeColor[0] * lobeA + 84 * lobeB + 150 * band) / total;
        d[idx + 1] = (70 * bloom + lobeColor[1] * lobeA + 70 * lobeB + 128 * band) / total;
        d[idx + 2] = (235 * bloom + lobeColor[2] * lobeA + 210 * lobeB + 240 * band) / total;
        d[idx + 3] = 255 * clamp01(total) * edgeFade(L, x, y, 72);
      }
    }
    hctx.putImageData(img, 0, 0);
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(haze, 0, 0, canvas.width, canvas.height);
    releaseCanvas(haze);

    ctx.setTransform(L.sx, 0, 0, L.sy, 0, 0);
    const rnd = mulberry32(SEED ^ 0x51ab);
    const ring = L.R * 1.04;
    function star(x, y, r, a, ci) {
      const dx = x - L.cx, dy = y - L.cy;
      if (dx * dx + dy * dy < ring * ring) return;
      const alpha = a * edgeFade(L, x, y, 26);
      if (alpha < 0.02) return;
      const half = r * DOT_SPREAD;
      ctx.globalAlpha = alpha;
      ctx.drawImage(dots, ci * DOT_CELL, 0, DOT_CELL, DOT_CELL, x - half, y - half, half * 2, half * 2);
    }
    const field = Math.round(L.w * L.h / 1500);
    for (let i = 0; i < field; i++) {
      const pick = rnd();
      star(rnd() * L.w, rnd() * L.h, 0.28 + 0.55 * Math.pow(rnd(), 2), 0.1 + 0.5 * Math.pow(rnd(), 2.2), pick < 0.35 ? 1 : (pick < 0.65 ? 2 : (pick < 0.9 ? 3 : 0)));
    }
    const bandStars = Math.round(150 * Math.min(1.4, L.w / 368));
    for (let i = 0; i < bandStars; i++) {
      const along = gauss(rnd) * 1.0, across = gauss(rnd) * 0.2;
      const x = L.cx + (1.2 + along * 0.78 + across * 0.62) * L.R;
      const y = L.cy + (1.3 - along * 0.62 + across * 0.78) * L.R;
      star(x, y, 0.24 + 0.42 * rnd(), 0.1 + 0.38 * Math.pow(rnd(), 1.6), rnd() < 0.5 ? 2 : (rnd() < 0.5 ? 3 : 1));
    }
    ctx.globalAlpha = 1;
  }

  /* ------------------------------------------------------------------ seeding */
  function pickColor(rnd, accentShare) {
    const v = rnd();
    if (v < accentShare) return ACCENT;
    const w = rnd();
    return w < 0.14 ? 0 : (w < 0.36 ? 1 : (w < 0.62 ? 2 : (w < 0.86 ? 3 : 4)));
  }

  function seedOrbitDust(o, rnd) {
    const def = o.def, cfg = def.dust, list = [];
    const clumps = [];
    for (let c = 0; c < (cfg.clumps || 0); c++) clumps.push({ at: rnd() * TAU, width: 0.22 + rnd() * 0.3 });
    const speed = o.dir * TAU / o.period;
    for (let i = 0; i < cfg.count; i++) {
      const bright = rnd() < 0.1;
      let theta, drift, sway;
      if (def.arc) {
        // Dust of the travelling arc stays inside it.
        theta = gauss(rnd) * def.arc.span * 0.3;
        drift = 1;
        sway = 0.05 + rnd() * 0.12;
      } else if (clumps.length && rnd() < 0.42) {
        const clump = clumps[(rnd() * clumps.length) | 0];
        theta = clump.at + gauss(rnd) * clump.width;
        drift = 1;
        sway = 0.04 + rnd() * 0.1;
      } else {
        theta = rnd() * TAU;
        drift = 0.9 + rnd() * 0.2;
        sway = rnd() * 0.03;
      }
      // Most dust hugs the path; a share scatters outwards into a thin disc.
      const offset = rnd() < (cfg.halo || 0) ? Math.abs(gauss(rnd)) * cfg.spread * 3.4 : gauss(rnd) * cfg.spread;
      // Where the particle will be when its path forms decides when it leaves the core.
      const arrive = theta + speed * 1.15 * drift;
      const fromRear = Math.abs(((arrive - 1.5 * Math.PI) % TAU + TAU + Math.PI) % TAU - Math.PI) / Math.PI;
      const length = 620 + rnd() * 300;
      const delay = Math.min(BIRTH_MS - 120 - length, def.form.at - 240 + def.form.ms * fromRear * 0.85 + rnd() * 240);
      list.push({
        th: theta, sp: drift, rho: 1 + offset, lift: gauss(rnd) * cfg.lift,
        wa: sway, ws: 0.2 + rnd() * 0.5, wp: rnd() * TAU,
        r: (bright ? 1.25 + rnd() * 0.65 : 0.42 + rnd() * 0.74) * cfg.size,
        a: (bright ? 0.76 + rnd() * 0.24 : 0.24 + rnd() * 0.58) * cfg.alpha,
        tw: rnd() * TAU, tws: 0.5 + rnd() * 1.3,
        ci: bright ? (rnd() < 0.5 ? 0 : 1) : pickColor(rnd, cfg.accent || 0.1),
        rank: rnd(),
        fd: delay, fl: length, rs: (0.82 + rnd() * 0.22) / def.radius, wind: 1.2 + rnd() * 1.5,
        vx: 0, vy: 0, vr: 0, va: 0, vf: false
      });
    }
    return list;
  }

  function seedStars(L, rnd) {
    const list = [];
    function add(x, y, r, a, hero) {
      if (x < 12 || x > L.w - 12 || y < 12 || y > L.h - 12) return;
      const dx = x - L.cx, dy = y - L.cy;
      if (dx * dx + dy * dy < L.R * L.R * 1.3) return;
      list.push({
        x: x, y: y, r: r, a: a, hero: hero,
        tw: rnd() * TAU, tws: 0.45 + rnd() * 0.85, dp: rnd() * TAU,
        bd: 560 + rnd() * 420, bl: 560 + rnd() * 300, swirl: (rnd() < 0.78 ? -1 : 1) * (0.35 + rnd() * 0.75),
        ci: rnd() < 0.3 ? 0 : 1,
        vx: 0, vy: 0, vr: 0, va: 0, tx: 0, ty: 0, ta: 0
      });
    }
    HERO_STARS.forEach(function (h) { add(L.cx + h[0] * L.R, L.cy + h[1] * L.R, h[2], 0.72 + rnd() * 0.16, true); });
    const count = Math.max(10, Math.min(26, Math.round(L.w * L.h / 9500)));
    for (let i = 0; i < count; i++) add(rnd() * L.w, rnd() * L.h, 0.55 + rnd() * 0.75, 0.24 + rnd() * 0.42, false);
    return list;
  }

  function seedDrift(L, rnd) {
    const list = [];
    const scale = Math.max(0.55, Math.min(1.15, L.h / 468));
    for (let i = 0; i < 64; i++) {
      const front = rnd() < 0.3;
      // Front motes stay off the timer column; rear ones may rise behind the core.
      const side = rnd() < 0.5 ? -1 : 1;
      const x = front ? L.cx + side * L.R * (0.72 + rnd() * 0.95) : L.cx + gauss(rnd) * L.R * 1.25;
      const rise = (70 + rnd() * 160) * scale;
      const base = L.cy + L.R * 0.12 + rnd() * Math.max(10, L.floor - L.cy - L.R * 0.12);
      list.push({
        x: Math.max(14, Math.min(L.w - 14, x)), base: base, rise: rise,
        cycle: rise / (5 + rnd() * 11), ph: rnd(),
        sa: 3 + rnd() * 6, sw: 0.3 + rnd() * 0.6, sp: rnd() * TAU, lean: gauss(rnd) * 10,
        r: 0.42 + rnd() * 0.8, a: 0.2 + rnd() * 0.42, ci: pickColor(rnd, 0.1), front: front, rank: rnd(),
        vx: 0, vy: 0, vr: 0, va: 0, vf: front
      });
    }
    return list;
  }

  // Short-lived formation sparks: staggered, slightly swirling, biased towards the orbit planes.
  function seedSparks(orbits, rnd) {
    const list = [];
    for (let i = 0; i < 96; i++) {
      let dx, dy, dz;
      if (rnd() < 0.6) {
        const o = orbits[rnd() < 0.42 ? 0 : 1 + ((rnd() * (orbits.length - 1)) | 0)];
        const theta = rnd() * TAU;
        const lx = Math.cos(theta), lz = Math.sin(theta), jitter = gauss(rnd) * 0.16;
        const y3 = lz * o.sinI * o.flip + jitter * o.cosI;
        dz = lz * o.cosI - jitter * o.sinI * o.flip;
        dx = lx * o.cosR - y3 * o.sinR;
        dy = lx * o.sinR + y3 * o.cosR;
      } else {
        // Two soft lobes instead of an even sphere keep the release asymmetric.
        const angle = (rnd() < 0.6 ? -0.75 : 2.5) + gauss(rnd) * 0.9;
        dz = gauss(rnd) * 0.5;
        dx = Math.cos(angle); dy = Math.sin(angle);
      }
      const len = Math.sqrt(dx * dx + dy * dy + dz * dz) || 1;
      const fast = rnd();
      const emit = 640 + 420 * Math.pow(rnd(), 1.7);
      list.push({
        dx: dx / len, dy: dy / len, dz: dz / len,
        te: emit, life: Math.min(420 + rnd() * 780, BIRTH_MS - 60 - emit),
        v: 0.9 + 4.4 * Math.pow(fast, 2.2), drag: 3.2 + rnd() * 2.8,
        swirl: (rnd() < 0.82 ? -1 : 1) * (1.6 + rnd() * 3.6), from: 0.78 + rnd() * 0.28,
        r: 0.4 + rnd() * 0.85, a: 0.4 + rnd() * 0.55, ci: pickColor(rnd, 0.08),
        trail: fast > 0.7, rank: rnd(),
        vx: 0, vy: 0, vr: 0, va: 0, vf: false, tx: 0, ty: 0
      });
    }
    return list;
  }

  // Stars inside the core, on slowly counter-rotating shells.
  function seedInner(rnd) {
    const list = [];
    for (let i = 0; i < 44; i++) {
      const y = rnd() * 2 - 1, angle = rnd() * TAU, ring = Math.sqrt(1 - y * y);
      const shell = 0.34 + rnd() * 0.58;
      list.push({
        x: ring * Math.cos(angle) * shell, y: y * shell, z: ring * Math.sin(angle) * shell,
        speed: (0.7 + rnd() * 0.6) * (shell < 0.62 ? 1.35 : 0.8) * (rnd() < 0.5 ? 1 : -0.6),
        r: 0.4 + rnd() * 0.6, a: 0.45 + rnd() * 0.55, ci: rnd() < 0.5 ? 1 : 2, tw: rnd() * TAU
      });
    }
    return list;
  }

  /* ------------------------------------------------------------------ scene build */
  function buildScene(state) {
    const L = state.layout;
    const rnd = mulberry32(SEED);
    const noise = createNoise(mulberry32(SEED ^ 0x9e37));
    const accent = state.accent;

    state.canvas.width = Math.round(L.w * L.dpr);
    state.canvas.height = Math.round(L.h * L.dpr);
    state.backdrop.width = state.canvas.width;
    state.backdrop.height = state.canvas.height;

    const palette = PALETTE.slice();
    palette[ACCENT] = accent;
    for (let i = 0; i < palette.length; i++) DOT_STYLES[i] = rgba(palette[i], 1);

    disposeSprites(state);
    const dots = bakeDots(palette);
    const core = bakeCore(L, noise, accent);
    state.sprites = {
      dots: dots,
      glow: bakeGlow([140, 100, 255]),
      cloud: bakeCloud(noise, [128, 84, 240]),
      core: core.core,
      dark: core.dark
    };
    bakeBackdrop(state.backdrop, L, dots, noise, accent);

    state.orbits = ORBITS.map(function (def, index) {
      const o = { def: def, index: index, dir: def.dir, period: def.period, flip: def.flip, a: 0, sinI: 0, cosI: 1, cosR: 1, sinR: 0, paint: null, dust: null };
      fitOrbit(o, L);
      paintOrbit(state.ctx, o, L, accent);
      o.dust = seedOrbitDust(o, rnd);
      return o;
    });
    state.stars = seedStars(L, rnd);
    state.drift = seedDrift(L, rnd);
    state.sparks = seedSparks(state.orbits, rnd);
    state.inner = seedInner(rnd);
  }

  function disposeSprites(state) {
    const s = state.sprites;
    if (!s) return;
    releaseCanvas(s.dots); releaseCanvas(s.glow); releaseCanvas(s.cloud); releaseCanvas(s.core); releaseCanvas(s.dark);
    state.sprites = null;
  }

  /* ------------------------------------------------------------------ entrance timeline */
  function resolveEntrance(state, ms) {
    const fx = state.fx, count = state.orbits.length;
    if (state.entrance === 'birth' && ms < BIRTH_MS) {
      // Seed, then one continuous expansion into the final core.
      const seed = 0.06 * easeOutCubic(clamp01(ms / 240));
      const x = clamp01((ms - 190) / 1000);
      fx.g = seed + 0.94 * (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2);
      fx.core = 1;
      fx.detail = smooth(0.2, 0.9, fx.g);
      const peak = Math.sin(Math.PI * clamp01((ms - 520) / 1050));
      fx.bloom = smooth(0.02, 0.7, fx.g) * (1 + 0.6 * peak * peak);
      fx.seed = (1 - smooth(0.1, 0.55, fx.g)) * clamp01(ms / 160);
      fx.lens = smooth(0, 0.06, fx.g) * (1 - smooth(0.25, 0.95, fx.g));
      fx.deep = smooth(100, 1150, ms);
      fx.field = 1;
      fx.crest = smooth(1150, 2000, ms);
      for (let i = 0; i < count; i++) {
        const form = state.orbits[i].def.form;
        fx.orbit[i] = 1;
        fx.line[i] = easeInOutCubic(clamp01((ms - form.at) / form.ms));
      }
      fx.forming = true;
      return;
    }
    if (state.entrance === 'reentry' && ms < REENTRY_MS) {
      fx.g = 0.94 + 0.06 * easeOutCubic(clamp01(ms / 720));
      fx.core = smooth(0, 460, ms);
      fx.detail = 1;
      fx.bloom = fx.core;
      fx.seed = 0;
      fx.lens = 0;
      fx.deep = 0.3 + 0.7 * smooth(0, 420, ms);
      fx.field = smooth(140, 820, ms);
      fx.crest = fx.field;
      for (let i = 0; i < count; i++) {
        fx.orbit[i] = smooth(90 + 70 * i, 620 + 70 * i, ms);
        fx.line[i] = 1;
      }
      fx.forming = false;
      return;
    }
    fx.g = 1; fx.core = 1; fx.detail = 1; fx.bloom = 1; fx.seed = 0; fx.lens = 0; fx.deep = 1; fx.field = 1; fx.crest = 1;
    for (let i = 0; i < count; i++) { fx.orbit[i] = 1; fx.line[i] = 1; }
    fx.forming = false;
    state.entranceDone = true;
  }

  /* ------------------------------------------------------------------ per-frame update */
  function rankFade(density, rank) {
    const k = (density + 0.06 - rank) / 0.06;
    return k <= 0 ? 0 : (k >= 1 ? 1 : k);
  }

  function updateScene(state, t, ms) {
    const L = state.layout, fx = state.fx, density = state.density;
    const forming = fx.forming;
    const hidden = Math.pow(L.R * fx.g * 0.97, 2); // Squared radius hidden behind the core.

    // Orbit dust
    for (let n = 0; n < state.orbits.length; n++) {
      const o = state.orbits[n], def = o.def, list = o.dust;
      const turn = o.dir * TAU * t / o.period;
      const origin = def.arc ? def.arc.phase : 0;
      const presence = fx.orbit[n] * fx.field;
      for (let i = 0; i < list.length; i++) {
        const p = list[i];
        const share = rankFade(density, p.rank);
        if (share <= 0 || presence <= 0.004) { p.va = 0; continue; }
        let theta = origin + p.th + turn * p.sp + p.wa * Math.sin(t * p.ws + p.wp);
        let rho = p.rho, lift = p.lift * L.R, alpha = p.a * share * presence;
        if (forming) {
          const k = (ms - p.fd) / p.fl;
          if (k <= 0) { p.va = 0; continue; }
          if (k < 1) {
            // Spiral out of the core into the orbit.
            const e = easeOutCubic(k), rest = 1 - e;
            rho *= p.rs + (1 - p.rs) * e;
            theta -= o.dir * p.wind * rest * rest;
            lift *= e;
            if (k < 0.3) alpha *= k / 0.3;
          }
        }
        projectOrbit(o, L, theta, rho, lift);
        const front = P.z >= 0, depth = P.z / o.a;
        if (front) {
          alpha *= (0.8 + 0.3 * depth) * zoneMask(L, P.x, P.y);
        } else {
          const dx = P.x - L.cx, dy = P.y - L.cy;
          if (dx * dx + dy * dy < hidden) { p.va = 0; continue; }
          alpha *= 0.62 + 0.4 * depth;
        }
        alpha *= (0.78 + 0.22 * Math.sin(p.tw + t * p.tws)) * edgeFade(L, P.x, P.y, 22);
        p.vx = P.x; p.vy = P.y; p.vr = p.r * P.s; p.va = alpha; p.vf = front;
      }
    }

    // Stars
    for (let i = 0; i < state.stars.length; i++) {
      const s = state.stars[i];
      let x = s.x + Math.sin(t * 0.05 + s.dp) * 1.3, y = s.y + Math.cos(t * 0.04 + s.dp) * 1;
      let alpha = s.a * (0.72 + 0.28 * Math.sin(s.tw + t * s.tws)) * fx.field;
      let radius = s.r;
      s.ta = 0;
      if (forming) {
        const k = (ms - s.bd) / s.bl;
        if (k <= 0) { s.va = 0; continue; }
        if (k < 1) {
          // Thrown out of the core on a short curved path, then it settles as a star.
          const e = easeOutQuint(k), rest = 1 - e, turn = s.swirl * rest * Math.sqrt(rest);
          const cs = Math.cos(turn), sn = Math.sin(turn);
          const dx = (x - L.cx) * e, dy = (y - L.cy) * e;
          const nx = L.cx + dx * cs - dy * sn, ny = L.cy + dx * sn + dy * cs;
          if (s.hero && k < 0.7) {
            const e2 = easeOutQuint(Math.max(0, k - 0.022)), rest2 = 1 - e2, turn2 = s.swirl * rest2 * Math.sqrt(rest2);
            const dx2 = (x - L.cx) * e2, dy2 = (y - L.cy) * e2;
            s.tx = L.cx + dx2 * Math.cos(turn2) - dy2 * Math.sin(turn2);
            s.ty = L.cy + dx2 * Math.sin(turn2) + dy2 * Math.cos(turn2);
            s.ta = (1 - k / 0.7) * 0.36;
          }
          x = nx; y = ny;
          alpha *= smooth(0, 0.22, k);
          radius *= 1 + 0.5 * rest;
        }
      }
      const dx = x - L.cx, dy = y - L.cy;
      if (dx * dx + dy * dy < hidden) { s.va = 0; s.ta = 0; continue; }
      s.vx = x; s.vy = y; s.vr = radius; s.va = alpha;
    }

    // Upward drift
    for (let i = 0; i < state.drift.length; i++) {
      const p = state.drift[i];
      const share = rankFade(density, p.rank);
      if (share <= 0) { p.va = 0; continue; }
      const turns = p.ph + (state.reduced ? 0 : t / p.cycle);
      const lap = Math.floor(turns), u = turns - lap;
      // Each lap rises in a slightly different column.
      const shift = Math.sin(lap * 12.9898 + i * 78.233) * 43758.5453;
      let x = p.x + (shift - Math.floor(shift) - 0.5) * 34 + Math.sin(t * p.sw + p.sp) * p.sa + p.lean * u;
      let y = p.base - u * p.rise;
      let alpha = p.a * share * fx.field * Math.pow(Math.sin(Math.PI * u), 0.7);
      const dx = x - L.cx, dy = y - L.cy, dist = Math.sqrt(dx * dx + dy * dy);
      // The field bends motes around the core and lifts their brightness slightly.
      const pull = 1 - smooth(L.R, L.R * 1.9, dist);
      if (pull > 0) {
        x += (dx < 0 ? -1 : 1) * pull * L.R * (p.front ? 0.08 : 0.14);
        alpha *= 1 + 0.7 * pull;
      }
      if (forming) {
        const k = (ms - 860 - 560 * p.rank) / 700;
        if (k <= 0) { p.va = 0; continue; }
        if (k < 1) {
          const e = easeOutCubic(k);
          x = L.cx + (x - L.cx) * (0.3 + 0.7 * e);
          y = L.cy + (y - L.cy) * (0.3 + 0.7 * e);
          alpha *= k;
        }
      }
      if (p.front) alpha *= zoneMask(L, x, y);
      else if ((x - L.cx) * (x - L.cx) + (y - L.cy) * (y - L.cy) < hidden) { p.va = 0; continue; }
      alpha *= edgeFade(L, x, y, 30) * smooth(L.floor + 4, L.floor - 22, y);
      p.vx = x; p.vy = y; p.vr = p.r; p.va = alpha;
    }

    // Formation sparks
    state.sparksLive = false;
    if (forming) {
      const share = state.tier.sparks;
      for (let i = 0; i < state.sparks.length; i++) {
        const p = state.sparks[i];
        const age = ms - p.te;
        if (age <= 0 || age >= p.life || p.rank >= share) { p.va = 0; continue; }
        state.sparksLive = true;
        const life = age / p.life, sec = age / 1000;
        const fade = (life < 0.1 ? life / 0.1 : 1) * Math.pow(1 - life, 1.6);
        sparkPoint(state, p, sec);
        const x = P.x, y = P.y, front = P.z >= 0, scale = P.s;
        let alpha = p.a * fade * edgeFade(L, x, y, 26);
        if (front) alpha *= zoneMask(L, x, y);
        else if ((x - L.cx) * (x - L.cx) + (y - L.cy) * (y - L.cy) < hidden) { p.va = 0; continue; }
        else alpha *= 0.7;
        p.vx = x; p.vy = y; p.vr = p.r * scale; p.va = alpha; p.vf = front;
        if (p.trail && state.tier.trails) {
          sparkPoint(state, p, Math.max(0, sec - 0.03));
          p.tx = P.x; p.ty = P.y;
        } else {
          p.tx = x; p.ty = y;
        }
      }
    }
  }

  function sparkPoint(state, p, sec) {
    const L = state.layout;
    const eased = 1 - Math.exp(-p.drag * sec);
    const start = state.emitRadius * p.from;
    const dist = start + (p.v * L.R / p.drag) * eased;
    const turn = (p.swirl / p.drag) * eased;
    const cs = Math.cos(turn), sn = Math.sin(turn);
    const x = (p.dx * cs - p.dy * sn) * dist, y = (p.dx * sn + p.dy * cs) * dist, z = p.dz * dist;
    const s = L.cam / (L.cam - z);
    P.x = L.cx + x * s; P.y = L.cy + y * s; P.z = z; P.s = s;
  }

  /* ------------------------------------------------------------------ drawing */
  function drawDot(ctx, dots, ci, x, y, r, alpha) {
    if (alpha < 0.012) return;
    const half = r * DOT_SPREAD;
    ctx.globalAlpha = alpha > 1 ? 1 : alpha;
    ctx.drawImage(dots, ci * DOT_CELL, 0, DOT_CELL, DOT_CELL, x - half, y - half, half + half, half + half);
  }

  function drawStars(state) {
    const ctx = state.ctx, S = state.sprites;
    for (let i = 0; i < state.stars.length; i++) {
      const s = state.stars[i];
      if (s.va < 0.012) continue;
      if (s.ta > 0.02) {
        ctx.globalAlpha = s.ta * s.va;
        ctx.strokeStyle = DOT_STYLES[s.ci];
        ctx.lineWidth = s.vr * 0.9;
        ctx.beginPath(); ctx.moveTo(s.tx, s.ty); ctx.lineTo(s.vx, s.vy); ctx.stroke();
      }
      if (s.hero) {
        const halo = s.vr * 7;
        ctx.globalAlpha = Math.min(1, s.va * 0.5);
        ctx.drawImage(S.glow, s.vx - halo, s.vy - halo, halo * 2, halo * 2);
      }
      drawDot(ctx, S.dots, s.ci, s.vx, s.vy, s.vr, s.va);
    }
  }

  function drawField(state, front) {
    const ctx = state.ctx, dots = state.sprites.dots;
    for (let n = 0; n < state.orbits.length; n++) {
      const list = state.orbits[n].dust;
      for (let i = 0; i < list.length; i++) {
        const p = list[i];
        if (p.vf === front && p.va >= 0.012) drawDot(ctx, dots, p.ci, p.vx, p.vy, p.vr, p.va);
      }
    }
    for (let i = 0; i < state.drift.length; i++) {
      const p = state.drift[i];
      if (p.vf === front && p.va >= 0.012) drawDot(ctx, dots, p.ci, p.vx, p.vy, p.vr, p.va);
    }
    if (!state.sparksLive) return;
    ctx.lineCap = 'round';
    for (let i = 0; i < state.sparks.length; i++) {
      const p = state.sparks[i];
      if (p.vf !== front || p.va < 0.012) continue;
      if (p.tx !== p.vx || p.ty !== p.vy) {
        ctx.globalAlpha = Math.min(1, p.va * 0.42);
        ctx.strokeStyle = DOT_STYLES[p.ci];
        ctx.lineWidth = p.vr * 1.2;
        ctx.beginPath(); ctx.moveTo(p.tx, p.ty); ctx.lineTo(p.vx, p.vy); ctx.stroke();
      }
      drawDot(ctx, dots, p.ci, p.vx, p.vy, p.vr, p.va);
    }
  }

  // Fills the parts of the arc [a0, a1] that lie on the requested side as tapered ribbons.
  function drawArc(ctx, o, L, a0, a1, front, width, taper, haloStyle, coreStyle, alpha, glow) {
    if (a1 - a0 < 0.0005 || alpha < 0.004) return;
    let from = a0;
    while (from < a1 - 0.000001) {
      const half = Math.floor(from / Math.PI + 0.000000001);
      const to = Math.min(a1, (half + 1) * Math.PI);
      if ((((half % 2) + 2) % 2 === 0) === front) {
        const n = Math.max(2, Math.min(ARC_POINTS, Math.ceil((to - from) / ARC_STEP)));
        for (let j = 0; j <= n; j++) {
          const theta = from + (to - from) * j / n;
          projectOrbit(o, L, theta, 1, 0);
          let w = width * P.s * 0.5;
          if (taper > 0) {
            const e = Math.min(theta - a0, a1 - theta) / taper;
            w *= e >= 1 ? 1 : (e <= 0 ? 0 : e * e * (3 - 2 * e));
          }
          RIBBON.x[j] = P.x; RIBBON.y[j] = P.y; RIBBON.w[j] = w;
        }
        for (let j = 0; j <= n; j++) {
          const a = j > 0 ? j - 1 : 0, b = j < n ? j + 1 : n;
          const tx = RIBBON.x[b] - RIBBON.x[a], ty = RIBBON.y[b] - RIBBON.y[a];
          const len = Math.sqrt(tx * tx + ty * ty) || 1;
          RIBBON.nx[j] = -ty / len; RIBBON.ny[j] = tx / len;
        }
        if (glow >= 2) fillRibbon(ctx, n, 7, haloStyle, alpha * 0.085);
        if (glow >= 1) fillRibbon(ctx, n, 3, haloStyle, alpha * 0.27);
        fillRibbon(ctx, n, 1, coreStyle, alpha);
      }
      from = to;
    }
  }

  function fillRibbon(ctx, n, widen, style, alpha) {
    ctx.globalAlpha = alpha > 1 ? 1 : alpha;
    ctx.fillStyle = style;
    ctx.beginPath();
    for (let j = 0; j <= n; j++) {
      const w = RIBBON.w[j] * widen;
      const x = RIBBON.x[j] + RIBBON.nx[j] * w, y = RIBBON.y[j] + RIBBON.ny[j] * w;
      if (j === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    }
    for (let j = n; j >= 0; j--) {
      const w = RIBBON.w[j] * widen;
      ctx.lineTo(RIBBON.x[j] - RIBBON.nx[j] * w, RIBBON.y[j] - RIBBON.ny[j] * w);
    }
    ctx.closePath();
    ctx.fill();
  }

  function drawPaths(state, t, front) {
    const ctx = state.ctx, L = state.layout, fx = state.fx;
    for (let n = 0; n < state.orbits.length; n++) {
      const o = state.orbits[n], def = o.def, line = def.line, paint = o.paint;
      if (!line || !paint) continue;
      const progress = fx.line[n], alpha = line.alpha * fx.orbit[n];
      if (progress <= 0 || alpha < 0.004) continue;
      const halo = front ? paint.haloF : paint.haloR, core = front ? paint.coreF : paint.coreR;
      const turn = o.dir * TAU * t / o.period;
      let a0 = 0, a1 = TAU, taper = 0, lead0 = false, lead1 = false;
      if (def.arc) {
        const half = def.arc.span * 0.5 * progress, centre = def.arc.phase + turn;
        a0 = centre - half; a1 = centre + half; taper = Math.min(def.arc.taper, half * 0.95);
      } else if (progress < 1) {
        // Paths are drawn progressively, starting behind the core.
        taper = 0.06 + 0.5 * (1 - progress);
        if (def.form.from === 'rear') {
          a0 = Math.PI * 1.5 - progress * Math.PI; a1 = Math.PI * 1.5 + progress * Math.PI; lead0 = lead1 = true;
        } else if (o.dir > 0) {
          a0 = def.form.start; a1 = a0 + progress * TAU; lead1 = true;
        } else {
          a1 = def.form.start; a0 = a1 - progress * TAU; lead0 = true;
        }
      }
      drawArc(ctx, o, L, a0, a1, front, line.width, taper, halo, core, alpha, line.glow);
      if (def.crests && fx.crest > 0.01 && progress >= 1) {
        const crest = front ? paint.crestF : paint.crestR;
        for (let c = 0; c < def.crests.length; c++) {
          const cr = def.crests[c], centre = cr.phase + turn;
          drawArc(ctx, o, L, centre - cr.span / 2, centre + cr.span / 2, front, line.width * cr.width, cr.span * 0.46, halo, crest, alpha * cr.gain * fx.crest, Math.min(1, line.glow));
        }
      }
      if (lead0 || lead1) {
        const glow = alpha * Math.sqrt(1 - progress) * smooth(0, 0.08, progress);
        for (let e = 0; e < 2; e++) {
          if (!(e === 0 ? lead0 : lead1)) continue;
          projectOrbit(o, L, e === 0 ? a0 : a1, 1, 0);
          if ((P.z >= 0) === front) drawDot(ctx, state.sprites.dots, 1, P.x, P.y, (0.9 + line.width * 0.55) * P.s, glow * (front ? zoneMask(L, P.x, P.y) : 0.7));
        }
      }
    }
  }

  function drawCore(state, t) {
    const ctx = state.ctx, L = state.layout, fx = state.fx, S = state.sprites;
    const g = fx.g;
    if (g <= 0.001 || fx.core <= 0.002) return;
    const R = L.R, cx = L.cx, cy = L.cy;
    const breathe = 0.5 + 0.5 * Math.sin(t * 0.62);
    const bloom = fx.core * fx.bloom * (0.12 + 0.07 * breathe);
    if (bloom > 0.004) {
      const size = R * (0.7 + 1.15 * g);
      ctx.globalAlpha = Math.min(1, bloom);
      ctx.drawImage(S.glow, cx - size, cy - size, size * 2, size * 2);
    }
    if (fx.seed > 0.004) {
      const size = R * g * 2.6 + 24;
      ctx.globalAlpha = Math.min(1, 0.95 * fx.seed);
      ctx.drawImage(S.glow, cx - size, cy - size, size * 2, size * 2);
    }
    const half = R * CORE_SPAN * g;
    if (fx.detail < 0.999) {
      ctx.globalAlpha = fx.core;
      ctx.drawImage(S.dark, cx - half, cy - half, half * 2, half * 2);
    }
    if (fx.detail > 0.004) {
      ctx.globalAlpha = fx.core * fx.detail;
      ctx.drawImage(S.core, cx - half, cy - half, half * 2, half * 2);
    }
    const life = fx.core * fx.detail;
    if (life > 0.02) {
      const rad = R * g;
      if (state.tier.clouds) {
        // Two nebula veils drifting against each other inside the core.
        ctx.save();
        ctx.beginPath();
        ctx.arc(cx, cy, rad * 0.985, 0, TAU);
        ctx.clip();
        ctx.globalCompositeOperation = 'lighter';
        const a = rad * 0.66, b = rad * 0.54;
        const ax = cx + rad * (-0.4 + 0.1 * Math.sin(t / 23)), ay = cy + rad * (-0.56 + 0.06 * Math.cos(t / 31));
        const bx = cx + rad * (0.12 + 0.12 * Math.cos(t / 29)), by = cy + rad * (-0.7 + 0.05 * Math.sin(t / 19));
        ctx.globalAlpha = life * (0.2 + 0.05 * breathe);
        ctx.drawImage(S.cloud, ax - a, ay - a, a * 2, a * 2);
        ctx.globalAlpha = life * 0.15;
        ctx.drawImage(S.cloud, bx - b, by - b, b * 2, b * 2);
        ctx.restore();
      }
      const count = Math.min(state.inner.length, state.tier.inner);
      const spin = state.reduced ? 0 : t * TAU / 150;
      for (let i = 0; i < count; i++) {
        const p = state.inner[i];
        const angle = spin * p.speed, cs = Math.cos(angle), sn = Math.sin(angle);
        const x3 = p.x * cs + p.z * sn, z3 = p.z * cs - p.x * sn;
        // Rotation axis leans towards the viewer.
        const y3 = p.y * 0.94 - z3 * 0.34, zf = p.y * 0.34 + z3 * 0.94;
        const x = cx + x3 * rad, y = cy + y3 * rad;
        const lit = smooth(-0.25, 0.75, -0.52 * x3 - 0.85 * y3);
        const alpha = p.a * life * (0.36 + 0.64 * (zf + 1) * 0.5) * (0.3 + 0.7 * lit) * zoneMask(L, x, y) * (0.8 + 0.2 * Math.sin(p.tw + t * 0.9));
        drawDot(ctx, S.dots, p.ci, x, y, p.r * (0.85 + 0.2 * zf), alpha);
      }
    }
    if (fx.lens > 0.01) {
      // Faint ring of bent light around the forming core.
      ctx.globalAlpha = 0.3 * fx.lens;
      ctx.strokeStyle = DOT_STYLES[3];
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(cx, cy, R * g * 1.3 + 2.5, 0, TAU);
      ctx.stroke();
    }
  }

  function render(state) {
    const L = state.layout, ctx = state.ctx, t = state.clock, ms = t * 1000;
    resolveEntrance(state, ms);
    const fx = state.fx;
    if (fx.deep !== state.deepShown) {
      state.deepShown = fx.deep;
      state.backdrop.style.opacity = fx.deep >= 1 ? '' : fx.deep.toFixed(3);
    }
    state.emitRadius = L.R * Math.max(0.5, fx.g);
    updateScene(state, t, ms);
    ctx.setTransform(L.sx, 0, 0, L.sy, 0, 0);
    ctx.globalCompositeOperation = 'source-over';
    ctx.clearRect(0, 0, L.w, L.h);
    drawStars(state);
    drawPaths(state, t, false);
    drawField(state, false);
    drawCore(state, t);
    drawPaths(state, t, true);
    drawField(state, true);
    ctx.globalAlpha = 1;
  }

  /* ------------------------------------------------------------------ loop and quality */
  function applyLayout(state) {
    const layout = measure(state.scene, state.tier);
    if (!layout) return false;
    state.layout = layout;
    state.digitChars = (state.digits && state.digits.textContent || '').length;
    buildScene(state);
    state.deepShown = -1;
    return true;
  }

  function layoutChanged(state) {
    const rect = state.scene.getBoundingClientRect();
    if (!(rect.width > 1 && rect.height > 1)) return false;
    const L = state.layout;
    if (!L) return true;
    if (Math.abs(rect.width - L.w) > 0.5 || Math.abs(rect.height - L.h) > 0.5) return true;
    const anchor = state.scene.querySelector('#ucCore');
    const box = anchor ? anchor.getBoundingClientRect() : null;
    return Boolean(box && box.width > 1 && Math.abs(box.width / 2 - L.R) > 0.5);
  }

  // Steps the visual quality down when frames stay slow. Density eases, nothing is rebuilt
  // except for the lowest tier, which also lowers the canvas resolution.
  function watchPerformance(state, frameMs) {
    const perf = state.perf;
    perf.frames++;
    if (perf.frames < 30 || frameMs > 250) return;
    perf.sum += frameMs;
    perf.count++;
    if (perf.count < 90) return;
    const average = perf.sum / perf.count;
    perf.average = average;
    perf.sum = 0;
    perf.count = 0;
    perf.strikes = average > 22.5 ? perf.strikes + 1 : 0;
    if (perf.strikes < 2) return;
    perf.strikes = 0;
    const next = TIER_ORDER[TIER_ORDER.indexOf(state.tier.name) + 1];
    if (!next) return;
    const lowerResolution = TIERS[next].dprCap < state.tier.dprCap;
    state.tier = TIERS[next];
    if (lowerResolution) state.layoutDirty = true;
  }

  function frame(now) {
    rafId = 0;
    const state = st;
    if (!state || !state.running) return;
    const elapsed = now - state.last;
    state.last = now;
    if (!state.layout || state.layoutDirty || now - state.checkedAt > 1500) {
      // The scene follows its wrapper and the timer text; both are re-read at a low rate.
      state.checkedAt = now;
      const chars = (state.digits && state.digits.textContent || '').length;
      if (!state.layout || state.layoutDirty || chars !== state.digitChars || layoutChanged(state)) {
        state.layoutDirty = false;
        if (!applyLayout(state) && !state.layout) { rafId = global.requestAnimationFrame(frame); return; }
      }
    }
    state.clock += Math.min(0.1, Math.max(0, elapsed) / 1000);
    const target = state.tier.density;
    if (state.density !== target) {
      const step = Math.min(0.1, elapsed / 1000) * 0.45;
      state.density = Math.abs(target - state.density) <= step ? target : state.density + (target > state.density ? step : -step);
    }
    render(state);
    watchPerformance(state, elapsed);
    rafId = global.requestAnimationFrame(frame);
  }

  function renderStill(state) {
    if (!state.layout && !applyLayout(state)) return;
    render(state);
  }

  function onVisibility() {
    if (document.hidden) pause(); else resume();
  }
  function onResize() {
    const state = st;
    if (!state) return;
    if (state.running) { state.checkedAt = 0; return; }
    if (state.reduced && !rafId) {
      // Reduced motion has no loop: repaint the still once after the layout settled.
      rafId = global.requestAnimationFrame(function () {
        rafId = 0;
        if (st !== state || !layoutChanged(state)) return;
        if (applyLayout(state)) render(state);
      });
    }
  }

  /* ------------------------------------------------------------------ public API */
  function start(options) {
    const opts = options || {};
    const scene = document.getElementById('ucScene');
    const canvas = document.getElementById('ucCosmicCanvas');
    const backdrop = document.getElementById('ucCosmicBackdrop');
    if (!scene || !canvas || !backdrop || typeof canvas.getContext !== 'function') return false;
    if (st) return true; // Already showing this Session's scene.
    if (releaseTimer !== null) { clearTimeout(releaseTimer); releaseTimer = null; }
    const ctx = canvas.getContext('2d');
    if (!ctx) return false;
    const reduced = prefersReducedMotion();
    const tone = THEME_TONES[opts.theme];
    const tier = TIERS[reduced ? 'medium' : pickTier()];
    st = {
      scene: scene, canvas: canvas, backdrop: backdrop, ctx: ctx,
      digits: scene.querySelector('#timerDigits'), digitChars: 0,
      reduced: reduced, tier: tier, density: tier.density,
      accent: tone ? mix(tone, [255, 255, 255], 0.4) : PALETTE[ACCENT],
      entrance: reduced ? 'none' : (opts.entrance === 'birth' ? 'birth' : 'reentry'), entranceDone: false,
      clock: reduced ? STATIC_CLOCK : 0, last: 0, checkedAt: 0, running: false, layoutDirty: false,
      layout: null, sprites: null, orbits: [], stars: [], drift: [], sparks: [], inner: [],
      sparksLive: false, emitRadius: 0, deepShown: -1,
      fx: { g: 1, core: 1, detail: 1, bloom: 1, seed: 0, lens: 0, deep: 1, field: 1, crest: 1, forming: false, orbit: new Float32Array(ORBITS.length), line: new Float32Array(ORBITS.length) },
      perf: { frames: 0, sum: 0, count: 0, strikes: 0, average: 0 },
      observer: null
    };
    const state = st;
    if (typeof global.ResizeObserver === 'function') {
      state.observer = new global.ResizeObserver(onResize);
      state.observer.observe(scene);
    } else {
      global.addEventListener('resize', onResize, { passive: true });
    }
    document.addEventListener('visibilitychange', onVisibility);
    if (applyLayout(state)) render(state);
    if (reduced) {
      // The first measurement can run before the screen has its size; retry once.
      if (!state.layout) rafId = global.requestAnimationFrame(function () { rafId = 0; if (st === state) renderStill(state); });
      return true;
    }
    if (!document.hidden) {
      state.running = true;
      state.last = state.checkedAt = global.performance.now();
      rafId = global.requestAnimationFrame(frame);
    }
    return true;
  }

  function pause() {
    if (rafId) { global.cancelAnimationFrame(rafId); rafId = 0; }
    if (st) st.running = false;
  }

  function resume() {
    const state = st;
    if (!state || state.running || state.reduced || document.hidden) return;
    // A background visit never replays the entrance or missed frames.
    if (!state.entranceDone && state.clock > 0) state.clock = Math.max(state.clock, BIRTH_MS / 1000);
    state.running = true;
    state.last = global.performance.now();
    state.checkedAt = 0;
    if (!rafId) rafId = global.requestAnimationFrame(frame);
  }

  function stop() {
    if (rafId) { global.cancelAnimationFrame(rafId); rafId = 0; }
    const state = st;
    st = null;
    if (!state) return;
    state.running = false;
    if (state.observer) state.observer.disconnect(); else global.removeEventListener('resize', onResize);
    document.removeEventListener('visibilitychange', onVisibility);
    disposeSprites(state);
    state.orbits = state.stars = state.drift = state.sparks = state.inner = [];
    // The last frame stays for the outgoing screen transition, then the bitmaps are freed.
    const canvas = state.canvas, backdrop = state.backdrop;
    if (releaseTimer !== null) clearTimeout(releaseTimer);
    releaseTimer = setTimeout(function () {
      releaseTimer = null;
      if (st) return;
      releaseCanvas(canvas);
      releaseCanvas(backdrop);
      backdrop.style.opacity = '';
    }, RELEASE_DELAY_MS);
  }

  function getState() {
    const state = st;
    if (!state) return { active: false, running: false, loop: rafId !== 0 };
    let orbitDust = 0;
    state.orbits.forEach(function (o) { orbitDust += o.dust.length; });
    return {
      active: true,
      running: state.running,
      loop: rafId !== 0,
      entrance: state.entrance,
      entranceDone: state.entranceDone,
      reduced: state.reduced,
      tier: state.tier.name,
      density: state.density,
      clockMs: Math.round(state.clock * 1000),
      dpr: state.layout ? state.layout.dpr : 0,
      averageFrameMs: state.perf.average,
      orbits: state.orbits.map(function (o) { return o.def.id; }),
      counts: { orbitDust: orbitDust, drift: state.drift.length, stars: state.stars.length, sparks: state.sparks.length, inner: state.inner.length }
    };
  }

  global.CosmicCore = Object.freeze({ start: start, stop: stop, pause: pause, resume: resume, getState: getState });
})(window);
