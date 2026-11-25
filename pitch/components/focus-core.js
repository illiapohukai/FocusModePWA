/**
 * Presentation-Native Cosmic Core & Mission Operator Canvas Engine
 * Faithful adaptation of the Focus Mode+ v17.9 Cosmic Core (3D weak-perspective elliptical orbits,
 * per-pixel FBM spherical nebula & fresnel rim shading, front/rear Z-split occlusion)
 * plus the deterministic Quick Focus → Cosmic Core matter convergence and Operator sprite player.
 */

const TAU = Math.PI * 2;
const CAMERA = 5.6;
const CORE_SPAN = 1.55;
const DOT_CELL = 32;
const DOT_SPREAD = 2.85;
const ARC_STEP = TAU / 96;
const ARC_POINTS = 112;
const STATIC_CLOCK = 7.4;
const SEED = 0x17c09e;

const PALETTE = [
  [255, 244, 232], // Warm cream dust
  [238, 240, 255], // Cool starlight
  [224, 214, 255], // Pale violet
  [196, 181, 253], // Soft lavender
  [167, 139, 250], // Restrained purple
  [139, 92, 246]   // Primary violet accent
];

const ORBIT_DEFS = [
  {
    id: "primary", radius: 1.58, incline: 0.31, roll: 7, flip: 1, period: 28, dir: 1,
    line: { width: 2.3, alpha: 0.95, glow: 2, core: [240, 232, 255], halo: [150, 108, 255] },
    crests: [{ phase: 0.9, span: 1.5, width: 1.45, gain: 0.9 }, { phase: 3.9, span: 0.9, width: 1.2, gain: 0.45 }],
    dustCount: 140, spread: 0.055, lift: 0.028, size: 1.0, alpha: 1.0
  },
  {
    id: "inclined", radius: 1.38, incline: 0.58, roll: -54, flip: 1, period: 19, dir: -1,
    line: { width: 1.1, alpha: 0.52, glow: 1, core: [214, 198, 255], halo: [139, 92, 246] },
    crests: [{ phase: 2.2, span: 1.1, width: 1.3, gain: 0.8 }],
    dustCount: 68, spread: 0.045, lift: 0.016, size: 0.88, alpha: 0.9
  },
  {
    id: "steep", radius: 1.50, incline: 0.48, roll: 61, flip: 1, period: 15, dir: 1,
    line: { width: 0.85, alpha: 0.36, glow: 1, core: [198, 184, 253], halo: [124, 92, 246] },
    crests: [{ phase: 5.1, span: 0.9, width: 1.3, gain: 0.8 }],
    dustCount: 48, spread: 0.04, lift: 0.014, size: 0.82, alpha: 0.78
  },
  {
    id: "arc", radius: 1.48, incline: 0.41, roll: -21, flip: -1, period: 34, dir: -1,
    line: { width: 1.15, alpha: 0.50, glow: 1, core: [226, 216, 255], halo: [150, 120, 250] },
    arc: { phase: 5.6, span: 2.5, taper: 1.0 },
    dustCount: 64, spread: 0.09, lift: 0.04, size: 0.94, alpha: 0.95
  }
];

function clamp01(v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
function smooth(e0, e1, v) {
  const k = clamp01((v - e0) / (e1 - e0));
  return k * k * (3 - 2 * k);
}
function easeOutCubic(k) { const m = 1 - clamp01(k); return 1 - m * m * m; }
function easeInOutCubic(k) {
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function rgba(c, a) {
  return `rgba(${Math.round(c[0])},${Math.round(c[1])},${Math.round(c[2])},${clamp01(a).toFixed(3)})`;
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
    fbm(x, y, octaves) {
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

function createOffscreen(w, h) {
  const c = document.createElement("canvas");
  c.width = Math.max(1, Math.round(w));
  c.height = Math.max(1, Math.round(h));
  return c;
}

function bakeDots() {
  const canvas = createOffscreen(DOT_CELL * PALETTE.length, DOT_CELL);
  const ctx = canvas.getContext("2d");
  const half = DOT_CELL / 2;
  for (let i = 0; i < PALETTE.length; i++) {
    const x = i * DOT_CELL + half;
    const grad = ctx.createRadialGradient(x, half, 0, x, half, half);
    grad.addColorStop(0, rgba(PALETTE[i], 1));
    grad.addColorStop(0.26, rgba(PALETTE[i], 0.94));
    grad.addColorStop(0.45, rgba(PALETTE[i], 0.30));
    grad.addColorStop(0.72, rgba(PALETTE[i], 0.07));
    grad.addColorStop(1, rgba(PALETTE[i], 0));
    ctx.fillStyle = grad;
    ctx.fillRect(i * DOT_CELL, 0, DOT_CELL, DOT_CELL);
  }
  return canvas;
}

function bakeGlow(color) {
  const size = 128, half = 64;
  const canvas = createOffscreen(size, size);
  const ctx = canvas.getContext("2d");
  const grad = ctx.createRadialGradient(half, half, 0, half, half, half);
  grad.addColorStop(0, rgba(color, 0.62));
  grad.addColorStop(0.24, rgba(color, 0.32));
  grad.addColorStop(0.54, rgba(color, 0.09));
  grad.addColorStop(1, rgba(color, 0));
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, size, size);
  return canvas;
}

function bakeCoreSprite(R, dpr, noise) {
  const size = Math.ceil(R * CORE_SPAN * 2 * dpr);
  const q = Math.max(128, Math.min(260, Math.round(R * 2 * dpr * 0.78)));
  const body = createOffscreen(q, q), bodyCtx = body.getContext("2d");
  const bodyImg = bodyCtx.createImageData(q, q), bd = bodyImg.data;
  const hx = -0.3, hy = -0.66, hz = Math.sqrt(1 - hx * hx - hy * hy);
  const accent = [158, 198, 246];

  for (let j = 0; j < q; j++) {
    const v0 = (j + 0.5) / q * 2 - 1;
    for (let i = 0; i < q; i++) {
      const u0 = (i + 0.5) / q * 2 - 1;
      const r = Math.sqrt(u0 * u0 + v0 * v0);
      const idx = (j * q + i) * 4;
      if (r > 1.05) continue;
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
      // Readability hollow in the center for the timer digits
      const ex = u / 0.62, ey = v / 0.38;
      const hollow = 1 - smooth(0.58, 1.38, Math.sqrt(ex * ex + ey * ey));
      const depth = 0.5 + 0.5 * Math.pow(nz, 0.6);
      const nb = cloud * (0.26 + 0.74 * lit) * (1 - 0.88 * hollow) * depth;
      const hot = nb * cloud * lit;
      const rim = fres * (0.12 + 0.88 * rimLit);
      const edge = fres * fres * rimLit;
      const counter = fres * smooth(0.15, 0.9, -ndl) * 0.3;
      const lens = Math.exp(-Math.pow((rc - 0.87) / 0.014, 2)) * (0.05 + 0.2 * lit);
      const ringY = 0.31 * 1.58 * 1.28 * Math.sqrt(Math.max(0, 1 - Math.pow(u / 1.7, 2))) + 0.12 * u;
      const sheen = Math.exp(-Math.pow((v - ringY + 0.05) / 0.13, 2)) * 0.2 * Math.pow(nz, 0.4);
      const nh = Math.max(0, u * hx + v * hy + nz * hz);
      const glint = Math.pow(nh, 620) + Math.pow(nh, 40) * 0.17;
      const baseR = 5 + 24 * lit * depth, baseG = 4 + 13 * lit * depth, baseB = 15 + 64 * lit * depth;
      bd[idx] = Math.min(255, baseR + 108 * nb + 96 * hot + 150 * rim + 96 * edge + 62 * counter + 170 * lens + accent[0] * sheen * 0.5 + 62 * sheen + 255 * glint);
      bd[idx + 1] = Math.min(255, baseG + 64 * nb + 84 * hot + 116 * rim + 110 * edge + 24 * counter + 150 * lens + accent[1] * sheen * 0.5 + 38 * sheen + 250 * glint);
      bd[idx + 2] = Math.min(255, baseB + 226 * nb + 30 * hot + 250 * rim + 20 * edge + 112 * counter + 255 * lens + accent[2] * sheen * 0.5 + 118 * sheen + 255 * glint);
      bd[idx + 3] = 255;
    }
  }
  bodyCtx.putImageData(bodyImg, 0, 0);

  // Atmospheric limb halo
  const hq = 128;
  const halo = createOffscreen(hq, hq), haloCtx = halo.getContext("2d");
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
      hd[idx] = 152 + 40 * toward;
      hd[idx + 1] = 114 + 44 * toward;
      hd[idx + 2] = 255;
      hd[idx + 3] = 255 * clamp01(a);
    }
  }
  haloCtx.putImageData(haloImg, 0, 0);

  const out = createOffscreen(size, size);
  const ctx = out.getContext("2d");
  const c = size / 2, rad = R * dpr;
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(halo, 0, 0, size, size);
  ctx.save();
  ctx.beginPath();
  ctx.arc(c, c, rad, 0, TAU);
  ctx.clip();
  ctx.drawImage(body, c - rad, c - rad, rad * 2, rad * 2);
  ctx.restore();

  // Crisp upper-left rim light
  const ring = createOffscreen(size, size), rctx = ring.getContext("2d");
  rctx.strokeStyle = rgba([238, 228, 255], 0.82);
  rctx.lineWidth = 1.2 * dpr;
  rctx.beginPath();
  rctx.arc(c, c, rad - 0.6 * dpr, 0, TAU);
  rctx.stroke();
  const mask = rctx.createLinearGradient(c - 0.52 * rad, c - 0.85 * rad, c + 0.16 * rad, c + 0.26 * rad);
  mask.addColorStop(0, "rgba(0,0,0,1)");
  mask.addColorStop(0.45, "rgba(0,0,0,0.5)");
  mask.addColorStop(1, "rgba(0,0,0,0)");
  rctx.globalCompositeOperation = "destination-in";
  rctx.fillStyle = mask;
  rctx.fillRect(0, 0, size, size);
  ctx.drawImage(ring, 0, 0);

  return out;
}

export class PitchCosmicCore {
  constructor(canvas, options = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.mode = options.mode || "core"; // 'core' | 'convergence' | 'portal'
    this.reduced = Boolean(options.reducedMotion);
    this.running = false;
    this.rafId = 0;
    this.clock = this.reduced ? STATIC_CLOCK : 0;
    this.birthMs = 0;
    this.birthDuration = 1650;
    this.converging = false;
    this.coreScale = 1;
    this.portalOpen = options.mode === "portal" ? 1 : 0;
    this.lastTime = 0;
    this.P = { x: 0, y: 0, z: 0, s: 1 };
    this.ribbon = {
      x: new Float32Array(ARC_POINTS + 1),
      y: new Float32Array(ARC_POINTS + 1),
      w: new Float32Array(ARC_POINTS + 1),
      nx: new Float32Array(ARC_POINTS + 1),
      ny: new Float32Array(ARC_POINTS + 1)
    };
    this.layout = null;
    this.sprites = null;
    this.orbits = [];
    this.innerStars = [];
    this.convergenceParticles = [];
    this._boundFrame = this._frame.bind(this);
  }

  measure() {
    const rect = this.canvas.getBoundingClientRect();
    const w = Math.max(320, rect.width || 640);
    const h = Math.max(320, rect.height || 520);
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const R = Math.min(w, h) * (this.mode === "portal" ? 0.29 : 0.25);
    const cx = w / 2;
    const cy = h / 2;
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    this.layout = { w, h, dpr, R, cx, cy, cam: R * CAMERA };
    this._buildSprites();
  }

  _buildSprites() {
    const L = this.layout;
    const rnd = mulberry32(SEED);
    const noise = createNoise(mulberry32(SEED ^ 0x9e37));
    this.sprites = {
      dots: bakeDots(),
      glow: bakeGlow([140, 100, 255]),
      core: bakeCoreSprite(L.R, L.dpr, noise)
    };

    this.orbits = ORBIT_DEFS.map((def) => {
      const roll = (def.roll * Math.PI) / 180;
      const o = {
        def,
        a: def.radius * L.R,
        sinI: def.incline,
        cosI: Math.sqrt(1 - def.incline * def.incline),
        cosR: Math.cos(roll),
        sinR: Math.sin(roll),
        dust: []
      };
      for (let i = 0; i < def.dustCount; i++) {
        const bright = rnd() < 0.14;
        o.dust.push({
          th: rnd() * TAU,
          sp: 0.88 + rnd() * 0.24,
          rho: 1 + gauss(rnd) * def.spread,
          lift: gauss(rnd) * def.lift,
          r: (bright ? 1.3 + rnd() * 0.6 : 0.48 + rnd() * 0.72) * def.size,
          a: (bright ? 0.8 + rnd() * 0.2 : 0.28 + rnd() * 0.56) * def.alpha,
          ci: bright ? 0 : ((rnd() * PALETTE.length) | 0),
          tw: rnd() * TAU,
          tws: 0.6 + rnd() * 1.2
        });
      }
      return o;
    });

    this.innerStars = [];
    for (let i = 0; i < 36; i++) {
      const y = rnd() * 2 - 1, angle = rnd() * TAU, ring = Math.sqrt(1 - y * y);
      const shell = 0.36 + rnd() * 0.56;
      this.innerStars.push({
        x: ring * Math.cos(angle) * shell,
        y: y * shell,
        z: ring * Math.sin(angle) * shell,
        speed: (0.7 + rnd() * 0.6) * (rnd() < 0.5 ? 1 : -0.65),
        r: 0.45 + rnd() * 0.55,
        a: 0.45 + rnd() * 0.5,
        ci: i % 2 === 0 ? 1 : 2,
        tw: rnd() * TAU
      });
    }

    // Seed 42 convergence matter particles for Quick Focus → Cosmic Core transition
    this.convergenceParticles = [];
    for (let i = 0; i < 42; i++) {
      const angle = (i / 42) * TAU + rnd() * 0.15;
      this.convergenceParticles.push({
        angle,
        startR: L.R * (1.25 + rnd() * 0.55),
        swirl: 1.8 + rnd() * 1.4,
        delay: (i % 7) * 0.03,
        size: 1.4 + (i % 3) * 0.65,
        ci: i % 4 === 0 ? 0 : (i % 2 === 0 ? 3 : 5)
      });
    }
  }

  _projectOrbit(o, theta, rho, lift) {
    const L = this.layout;
    const a = o.a * rho;
    const lx = a * Math.cos(theta);
    const lz = a * Math.sin(theta);
    const y3 = lz * o.sinI * o.def.flip + lift * o.cosI;
    const z3 = lz * o.cosI - lift * o.sinI * o.def.flip;
    const s = L.cam / (L.cam - z3);
    const X = lx * s, Y = y3 * s;
    this.P.x = L.cx + X * o.cosR - Y * o.sinR;
    this.P.y = L.cy + X * o.sinR + Y * o.cosR;
    this.P.z = z3;
    this.P.s = s;
  }

  _zoneMask(x, y) {
    if (this.mode === "portal") return 1;
    const L = this.layout;
    const zw = L.R * 0.72, zh = L.R * 0.36;
    const dx = Math.max(0, Math.abs(x - L.cx) - zw);
    const dy = Math.max(0, Math.abs(y - L.cy) - zh);
    if (dx === 0 && dy === 0) return 0.06;
    const d = Math.min(1, Math.sqrt(dx * dx + dy * dy) / 26);
    return 0.06 + 0.94 * d * d * (3 - 2 * d);
  }

  _drawDot(ci, x, y, r, alpha) {
    if (alpha < 0.015) return;
    const half = r * DOT_SPREAD;
    this.ctx.globalAlpha = Math.min(1, alpha);
    this.ctx.drawImage(this.sprites.dots, ci * DOT_CELL, 0, DOT_CELL, DOT_CELL, x - half, y - half, half * 2, half * 2);
  }

  _drawOrbitPaths(t, front, orbitProgress) {
    const ctx = this.ctx;
    for (let n = 0; n < this.orbits.length; n++) {
      const o = this.orbits[n], def = o.def, line = def.line;
      if (!line || orbitProgress <= 0.01) continue;
      const turn = def.dir * TAU * t / def.period;
      let a0 = 0, a1 = TAU * orbitProgress, taper = orbitProgress < 1 ? 0.25 : 0;
      if (def.arc) {
        const half = def.arc.span * 0.5 * orbitProgress;
        const centre = def.arc.phase + turn;
        a0 = centre - half;
        a1 = centre + half;
        taper = Math.min(def.arc.taper, half * 0.95);
      }
      const alpha = line.alpha * orbitProgress * (front ? 0.92 : 0.32);
      this._drawArcRibbon(o, a0, a1, front, line.width, taper, rgba(line.halo, alpha * 0.32), rgba(line.core, alpha));
    }
  }

  _drawArcRibbon(o, a0, a1, front, width, taper, haloColor, coreColor) {
    if (a1 - a0 < 0.002) return;
    const ctx = this.ctx, R = this.ribbon;
    let from = a0;
    while (from < a1 - 0.0001) {
      const half = Math.floor(from / Math.PI + 0.000001);
      const to = Math.min(a1, (half + 1) * Math.PI);
      if ((((half % 2) + 2) % 2 === 0) === front) {
        const n = Math.max(2, Math.min(ARC_POINTS, Math.ceil((to - from) / ARC_STEP)));
        for (let j = 0; j <= n; j++) {
          const theta = from + ((to - from) * j) / n;
          this._projectOrbit(o, theta, 1, 0);
          let w = width * this.P.s * 0.5;
          if (taper > 0) {
            const e = Math.min(theta - a0, a1 - theta) / taper;
            w *= e >= 1 ? 1 : (e <= 0 ? 0 : e * e * (3 - 2 * e));
          }
          if (front) w *= 0.35 + 0.65 * this._zoneMask(this.P.x, this.P.y);
          R.x[j] = this.P.x;
          R.y[j] = this.P.y;
          R.w[j] = w;
        }
        for (let j = 0; j <= n; j++) {
          const a = j > 0 ? j - 1 : 0, b = j < n ? j + 1 : n;
          const tx = R.x[b] - R.x[a], ty = R.y[b] - R.y[a];
          const len = Math.sqrt(tx * tx + ty * ty) || 1;
          R.nx[j] = -ty / len;
          R.ny[j] = tx / len;
        }
        this._fillRibbon(ctx, n, 3.2, haloColor);
        this._fillRibbon(ctx, n, 1.0, coreColor);
      }
      from = to;
    }
  }

  _fillRibbon(ctx, n, widen, fillStyle) {
    const R = this.ribbon;
    ctx.globalAlpha = 1;
    ctx.fillStyle = fillStyle;
    ctx.beginPath();
    for (let j = 0; j <= n; j++) {
      const w = R.w[j] * widen;
      const x = R.x[j] + R.nx[j] * w, y = R.y[j] + R.ny[j] * w;
      if (j === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    }
    for (let j = n; j >= 0; j--) {
      const w = R.w[j] * widen;
      ctx.lineTo(R.x[j] - R.nx[j] * w, R.y[j] - R.ny[j] * w);
    }
    ctx.closePath();
    ctx.fill();
  }

  _drawOrbitDust(t, front, orbitProgress) {
    if (orbitProgress <= 0.02) return;
    const L = this.layout;
    const hiddenSq = Math.pow(L.R * this.coreScale * 0.96, 2);
    for (let n = 0; n < this.orbits.length; n++) {
      const o = this.orbits[n], def = o.def;
      const turn = def.dir * TAU * t / def.period;
      for (let i = 0; i < o.dust.length; i++) {
        const p = o.dust[i];
        const theta = p.th + turn * p.sp;
        this._projectOrbit(o, theta, p.rho, p.lift * L.R);
        const isFront = this.P.z >= 0;
        if (isFront !== front) continue;
        const dx = this.P.x - L.cx, dy = this.P.y - L.cy;
        if (!isFront && dx * dx + dy * dy < hiddenSq) continue;
        let alpha = p.a * orbitProgress * (0.76 + 0.24 * Math.sin(p.tw + t * p.tws));
        if (isFront) alpha *= this._zoneMask(this.P.x, this.P.y);
        else alpha *= 0.65;
        this._drawDot(p.ci, this.P.x, this.P.y, p.r * this.P.s, alpha);
      }
    }
  }

  triggerConvergenceBirth() {
    if (this.reduced) {
      this.converging = false;
      this.coreScale = 1;
      this.clock = STATIC_CLOCK;
      this.renderFrame();
      return;
    }
    this.converging = true;
    this.birthMs = 0;
    this.clock = 0;
    if (!this.running) this.start();
  }

  renderFrame() {
    if (!this.layout) this.measure();
    const L = this.layout, ctx = this.ctx, S = this.sprites;
    const t = this.clock;
    ctx.setTransform(L.dpr, 0, 0, L.dpr, 0, 0);
    ctx.clearRect(0, 0, L.w, L.h);

    let coreG = this.coreScale;
    let orbitProgress = 1;
    let seedFlash = 0;

    if (this.converging) {
      const k = clamp01(this.birthMs / this.birthDuration);
      // Phase 1 (0..0.42): Matter spirals inward from Quick Focus dial radius into dark seed
      // Phase 2 (0.28..0.88): Compact dark violet sphere expands into full Cosmic Core
      // Phase 3 (0.45..1.0): 3D Elliptical orbits unfurl around the core
      coreG = k < 0.22
        ? 0.14 * easeOutCubic(k / 0.22)
        : 0.14 + 0.86 * easeInOutCubic((k - 0.22) / 0.66);
      orbitProgress = smooth(0.42, 0.98, k);
      seedFlash = Math.sin(Math.PI * clamp01((k - 0.12) / 0.45));

      // Draw inward-spiraling Quick Focus orbit matter
      if (k < 0.62) {
        const matterK = clamp01(k / 0.56);
        for (let i = 0; i < this.convergenceParticles.length; i++) {
          const cp = this.convergenceParticles[i];
          const pk = clamp01((matterK - cp.delay) / (1 - cp.delay));
          if (pk <= 0 || pk >= 1) continue;
          const ease = pk * pk;
          const curR = cp.startR * (1 - ease * 0.92);
          const curAngle = cp.angle + cp.swirl * ease * 2.4;
          const px = L.cx + Math.cos(curAngle) * curR;
          const py = L.cy + Math.sin(curAngle) * curR;
          const alpha = Math.sin(Math.PI * pk) * 0.95;
          this._drawDot(cp.ci, px, py, cp.size * (1 - 0.4 * ease), alpha);
        }
      }
      if (k >= 1) this.converging = false;
    }

    // 1. Rear orbit paths & dust (occluded behind sphere)
    this._drawOrbitPaths(t, false, orbitProgress);
    this._drawOrbitDust(t, false, orbitProgress);

    // 2. Central Cosmic Core Sphere & Internal Nebula/Stars
    if (coreG > 0.01) {
      const breathe = 0.5 + 0.5 * Math.sin(t * 0.65);
      const glowSize = L.R * (0.85 + 1.15 * coreG);
      ctx.globalAlpha = 0.24 + 0.08 * breathe + 0.35 * seedFlash;
      ctx.drawImage(S.glow, L.cx - glowSize, L.cy - glowSize, glowSize * 2, glowSize * 2);

      const half = L.R * CORE_SPAN * coreG;
      ctx.globalAlpha = 1;
      ctx.drawImage(S.core, L.cx - half, L.cy - half, half * 2, half * 2);

      // Counter-rotating inner star shells
      const rad = L.R * coreG;
      const spin = this.reduced ? 0 : (t * TAU) / 140;
      for (let i = 0; i < this.innerStars.length; i++) {
        const p = this.innerStars[i];
        const angle = spin * p.speed, cs = Math.cos(angle), sn = Math.sin(angle);
        const x3 = p.x * cs + p.z * sn, z3 = p.z * cs - p.x * sn;
        const y3 = p.y * 0.94 - z3 * 0.34, zf = p.y * 0.34 + z3 * 0.94;
        const x = L.cx + x3 * rad, y = L.cy + y3 * rad;
        const alpha = p.a * (0.4 + 0.6 * (zf + 1) * 0.5) * this._zoneMask(x, y);
        this._drawDot(p.ci, x, y, p.r, alpha);
      }
    }

    // 3. Front orbit paths & dust (passing in front of sphere)
    this._drawOrbitPaths(t, true, orbitProgress);
    this._drawOrbitDust(t, true, orbitProgress);
    ctx.globalAlpha = 1;
  }

  _frame(now) {
    this.rafId = 0;
    if (!this.running) return;
    const dt = Math.min(100, Math.max(0, now - this.lastTime));
    this.lastTime = now;
    this.clock += dt / 1000;
    if (this.converging) this.birthMs += dt;
    this.renderFrame();
    this.rafId = window.requestAnimationFrame(this._boundFrame);
  }

  start() {
    if (!this.layout) this.measure();
    if (this.reduced) {
      this.clock = STATIC_CLOCK;
      this.renderFrame();
      return;
    }
    if (this.running) return;
    this.running = true;
    this.lastTime = performance.now();
    this.renderFrame();
    this.rafId = window.requestAnimationFrame(this._boundFrame);
  }

  pause() {
    this.running = false;
    if (this.rafId) {
      window.cancelAnimationFrame(this.rafId);
      this.rafId = 0;
    }
  }

  reset() {
    this.pause();
    this.clock = this.reduced ? STATIC_CLOCK : 0;
    this.converging = false;
    this.birthMs = 0;
    this.coreScale = 1;
    this.renderFrame();
  }
}

/**
 * Deterministic Mission Generation Operator Sprite-Sheet Player (Scene 4 Demo Beat 2)
 * Uses local pitch/assets/operator/mission-operator-work.png & mission-operator-success.png
 */
export class PitchOperatorPlayer {
  constructor(canvas, options = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.reduced = Boolean(options.reducedMotion);
    this.metaUrl = options.metaUrl || "./assets/operator/mission-operator.json";
    this.loaded = null;
    this.running = false;
    this.rafId = 0;
    this.startTime = 0;
    this.onStageChange = options.onStageChange || null;
    this._boundTick = this._tick.bind(this);
  }

  async preload() {
    if (this.loaded) return this.loaded;
    const res = await fetch(this.metaUrl);
    const meta = await res.json();
    const base = new URL(this.metaUrl, document.baseURI);
    const loadImg = (rel) =>
      new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve(img);
        img.onerror = reject;
        img.src = new URL(rel, base).href;
      });
    const [work, success] = await Promise.all([
      loadImg(meta.work.image),
      loadImg(meta.success.image)
    ]);
    this.canvas.width = meta.frameWidth;
    this.canvas.height = meta.frameHeight;
    this.ctx.imageSmoothingEnabled = false;
    this.loaded = { meta, work, success };
    // Draw initial frame immediately
    this._drawFrame(work, meta.work, meta, 0);
    return this.loaded;
  }

  _drawFrame(sheet, seg, meta, idx) {
    const fw = meta.frameWidth, fh = meta.frameHeight;
    const col = idx % seg.columns;
    const row = Math.floor(idx / seg.columns);
    this.ctx.clearRect(0, 0, fw, fh);
    this.ctx.drawImage(sheet, col * fw, row * fh, fw, fh, 0, 0, fw, fh);
  }

  async playSequence() {
    this.stop();
    const data = await this.preload();
    if (this.reduced) {
      // In reduced-motion mode, show the completed planet thought-cloud frame immediately
      this._drawFrame(data.success, data.meta.success, data.meta, data.meta.success.frameCount - 1);
      if (this.onStageChange) this.onStageChange(2);
      return;
    }
    this.running = true;
    this.startTime = performance.now();
    if (this.onStageChange) this.onStageChange(0);
    this.rafId = window.requestAnimationFrame(this._boundTick);
  }

  _tick(now) {
    this.rafId = 0;
    if (!this.running || !this.loaded) return;
    const { meta, work, success } = this.loaded;
    const elapsed = now - this.startTime;
    // 0..1400ms: work loop (16 frames at 12fps)
    // 1400..3400ms: one-shot success planet sequence (24 frames at 12fps)
    const workDuration = 1400;
    if (elapsed < workDuration) {
      const frameIdx = Math.floor((elapsed / 1000) * meta.work.fps) % meta.work.frameCount;
      this._drawFrame(work, meta.work, meta, frameIdx);
      if (elapsed > 700 && this.onStageChange) this.onStageChange(1);
      this.rafId = window.requestAnimationFrame(this._boundTick);
    } else {
      const sElapsed = elapsed - workDuration;
      const frameIdx = Math.min(
        meta.success.frameCount - 1,
        Math.floor((sElapsed / 1000) * meta.success.fps)
      );
      this._drawFrame(success, meta.success, meta, frameIdx);
      if (frameIdx < meta.success.frameCount - 1) {
        this.rafId = window.requestAnimationFrame(this._boundTick);
      } else {
        this.running = false;
        if (this.onStageChange) this.onStageChange(2);
      }
    }
  }

  stop() {
    this.running = false;
    if (this.rafId) {
      window.cancelAnimationFrame(this.rafId);
      this.rafId = 0;
    }
  }
}
