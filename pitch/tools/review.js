#!/usr/bin/env node
/*
 * Focus Mode+ Pitch · deterministic review walkthrough.
 *
 *   node pitch/tools/review.js            # full review into pitch/review/
 *   node pitch/tools/review.js --quick    # checks only, no screenshots
 *
 * Needs only Node and Playwright with Chromium (preinstalled in the development container; locally:
 * any Playwright install). Serves pitch/ on its own origin (no PWA service worker), captures the
 * opening, strongest and ending state of every scene at 1920 x 1080, the ending states at
 * 1366 x 768 and with reduced motion, recording and rehearsal samples, then builds a contact sheet.
 *
 * Automated checks: console errors, failed or non-local requests, raw [PLACEHOLDER] tokens on stage,
 * visible text inside the camera-safe area, visible text outside the stage, JS loops left running
 * in hidden scenes, and deterministic pause/restart.
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'review');
const PORT = 4300 + Math.floor(Math.random() * 400);
const QUICK = process.argv.includes('--quick');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.json': 'application/json' };

function serve() {
  return new Promise(resolve => {
    const server = http.createServer((req, res) => {
      const rel = decodeURIComponent(new URL(req.url, 'http://x').pathname).replace(/^\/+/, '') || 'index.html';
      const file = path.join(ROOT, rel);
      if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); res.end(); return; }
      res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
      fs.createReadStream(file).pipe(res);
    });
    server.listen(PORT, '127.0.0.1', () => resolve(server));
  });
}

// [scene, id, label, setup]; setup: { beat } settled, or { from, wait } = press Space from beat `from`.
const PLAN = [
  [1, 'opening', 'Hook · Plan', { from: -1, wait: 1900 }],
  [1, 'strongest', 'Hook · Plan zerfällt', { from: 0, wait: 2300 }],
  [1, 'ending', 'Hook · Ein Plan ist noch kein Fortschritt', { beat: 2 }],
  [2, 'opening', 'Problem · 44 %', { from: -1, wait: 900 }],
  [2, 'strongest', 'Problem · Ablenkung', { beat: 0 }],
  [2, 'ending', 'Problem · Getrennte Werkzeuge', { beat: 1 }],
  [3, 'opening', 'Warum jetzt · KI-Nutzung', { beat: 0 }],
  [3, 'strongest', 'These · Verbindung entsteht', { from: 0, wait: 1700 }],
  [3, 'ending', 'These · Focus Mode+ verbindet', { beat: 2 }],
  [4, 'opening', 'Demo · Ziel, Deadline, Material', { from: -1, wait: 1200 }],
  [4, 'generate', 'Demo · Mission entsteht', { from: 0, wait: 1500 }],
  [4, 'route', 'Demo · Mission-Route', { beat: 2 }],
  [4, 'next', 'Demo · Nächster Schritt', { beat: 3 }],
  [4, 'protect', 'Demo · Fokus-Schutz', { beat: 4 }],
  [4, 'strongest', 'Demo · Quick Focus wird Cosmic Core', { from: 4, wait: 3500 }],
  [4, 'active', 'Demo · Aktiver Fokus', { beat: 6 }],
  [4, 'ending', 'Demo · Ergebnis fließt in den Plan', { beat: 7 }],
  [5, 'opening', 'Fortschritt · Route', { from: -1, wait: 2600 }],
  [5, 'strongest', 'Fortschritt · Gebaut', { beat: 1 }],
  [5, 'ending', 'Validierung · Noch zu beweisen', { beat: 2 }],
  [6, 'opening', 'Markt · 2,9 Mio.', { beat: 0 }],
  [6, 'strongest', 'Markt · Vergleich', { beat: 1 }],
  [6, 'ending', 'Modell · Free und Pro', { beat: 2 }],
  [7, 'opening', 'Pilot · Portal', { from: -1, wait: 1500 }],
  [7, 'strongest', 'Pilot · Messroute', { beat: 1 }],
  [7, 'ending', 'Pilot · Die ersten echten Nutzer', { beat: 2 }]
];

const problems = [];
const note = (kind, msg) => problems.push(kind + ': ' + msg);

async function open(browser, opts) {
  const context = await browser.newContext({ viewport: { width: opts.w || 1920, height: opts.h || 1080 }, deviceScaleFactor: 1, reducedMotion: opts.reduced ? 'reduce' : 'no-preference' });
  const page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') note('console', m.text()); });
  page.on('pageerror', e => note('pageerror', e.message));
  page.on('request', r => { const u = r.url(); if (!u.startsWith('http://127.0.0.1:' + PORT + '/') && !u.startsWith('data:')) note('remote request', u); });
  page.on('response', r => { if (r.status() >= 400) note('http ' + r.status(), r.url()); });
  page.on('requestfailed', r => note('request failed', r.url()));
  return { context, page };
}

async function goto(page, query) {
  await page.goto('http://127.0.0.1:' + PORT + '/index.html' + query, { waitUntil: 'load' });
  await page.waitForFunction(() => window.__pitchReady === true, null, { timeout: 10000 });
  await page.waitForTimeout(250);
}

async function stageState(page, scene, setup, mode) {
  const m = mode ? 'mode=' + mode + '&' : '';
  if (setup.beat != null) { await goto(page, '?' + m + 'scene=' + scene + '&beat=' + setup.beat); await page.waitForTimeout(700); return; }
  if (setup.from === -1) {
    await goto(page, '?' + m + 'scene=' + scene + '&beat=0');
    await page.keyboard.press('r');
  } else {
    await goto(page, '?' + m + 'scene=' + scene + '&beat=' + setup.from);
    await page.waitForTimeout(500);
    await page.keyboard.press('Space');
  }
  await page.waitForTimeout(setup.wait);
}

/* In-page audit of the current stage. */
async function audit(page, label) {
  const r = await page.evaluate(() => {
    const stage = document.getElementById('stage');
    const sr = stage.getBoundingClientRect(), k = sr.width / 1920;
    const safe = { x0: 1920 - 320, y1: 220 };
    const out = { tokens: [], safe: [], outside: [] };
    const active = document.querySelector('.scene.is-active');
    if (!active) return out;
    function visible(el) {
      let op = 1;
      for (let e = el; e && e !== stage; e = e.parentElement) {
        const cs = getComputedStyle(e);
        if (cs.display === 'none' || cs.visibility === 'hidden') return 0;
        op *= parseFloat(cs.opacity);
      }
      return op;
    }
    const walker = document.createTreeWalker(active, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = walker.nextNode())) {
      const text = n.textContent.trim();
      if (!text) continue;
      const el = n.parentElement;
      const op = visible(el);
      if (op < 0.2) continue;
      if (/\[[A-Z0-9_]+\]/.test(text)) out.tokens.push(text);
      const range = document.createRange(); range.selectNodeContents(n);
      const b = range.getBoundingClientRect();
      if (!b.width) continue;
      const x0 = (b.left - sr.left) / k, y0 = (b.top - sr.top) / k, x1 = (b.right - sr.left) / k, y1 = (b.bottom - sr.top) / k;
      const decorative = el.closest('.task, .hk-cal, .hk-notif');
      if (x1 > safe.x0 && y0 < safe.y1 && !decorative) out.safe.push(text.slice(0, 40) + ' @' + Math.round(x0) + ',' + Math.round(y0));
      if ((x0 < -1 || y0 < -1 || x1 > 1921 || y1 > 1081) && !decorative) out.outside.push(text.slice(0, 40) + ' @' + Math.round(x0) + ',' + Math.round(y0) + '–' + Math.round(x1) + ',' + Math.round(y1));
    }
    return out;
  });
  r.tokens.forEach(t => note('placeholder token visible [' + label + ']', t));
  r.safe.forEach(t => note('text in camera-safe area [' + label + ']', t));
  r.outside.forEach(t => note('text outside stage [' + label + ']', t));
}

async function shot(page, dir, name) {
  if (QUICK) return null;
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, name + '.jpg');
  await page.screenshot({ path: file, type: 'jpeg', quality: 84 });
  return file;
}

async function contactSheet(browser, entries) {
  const cells = entries.map(e => '<figure><img src="' + path.relative(OUT, e.file).split(path.sep).join('/') + '"><figcaption><b>' + e.scene + ' · ' + e.id + '</b> ' + e.label + '</figcaption></figure>').join('');
  const html = '<!DOCTYPE html><meta charset="utf-8"><style>body{margin:0;padding:28px;background:#1D1A17;font:600 15px Inter,Arial,sans-serif;color:#F6F1E7}h1{margin:0 0 18px;font-size:24px}' +
    '.g{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}figure{margin:0}img{display:block;width:100%;border-radius:8px}figcaption{margin-top:6px;color:#CFC3E6}b{color:#fff}</style>' +
    '<h1>Focus Mode+ · Pitch review · 1920 × 1080 · ' + new Date().toISOString().slice(0, 10) + '</h1><div class="g">' + cells + '</div>';
  const file = path.join(OUT, 'contact-sheet.html');
  fs.writeFileSync(file, html);
  const ctx = await browser.newContext({ viewport: { width: 2400, height: 1200 }, deviceScaleFactor: 1 });
  const p = await ctx.newPage();
  await p.goto('file://' + file);
  await p.waitForTimeout(500);
  await p.screenshot({ path: path.join(OUT, 'contact-sheet.jpg'), type: 'jpeg', quality: 82, fullPage: true });
  await ctx.close();
}

(async () => {
  const server = await serve();
  const browser = await chromium.launch();
  try {
    const entries = [];
    // 1920 x 1080 · recording mode (the capture path) · every planned state.
    {
      const { context, page } = await open(browser, {});
      for (const [scene, id, label, setup] of PLAN) {
        await stageState(page, scene, setup, 'recording');
        await audit(page, scene + '/' + id);
        const file = await shot(page, path.join(OUT, '1920x1080'), 'scene-' + scene + '-' + id);
        if (file) entries.push({ scene, id, label, file });
      }
      await context.close();
    }
    // 1366 x 768 and 1440 x 900 / 1600 x 900 · settled ending states.
    for (const vp of [[1366, 768], [1440, 900], [1600, 900]]) {
      const { context, page } = await open(browser, { w: vp[0], h: vp[1] });
      for (const [scene, id, , setup] of PLAN.filter(p => p[1] === 'ending' || p[1] === 'strongest')) {
        if (setup.beat == null) continue;
        await stageState(page, scene, setup, 'recording');
        await audit(page, vp.join('x') + ' ' + scene + '/' + id);
        if (vp[0] === 1366) await shot(page, path.join(OUT, '1366x768'), 'scene-' + scene + '-' + id);
      }
      const hs = await page.evaluate(() => [document.documentElement.scrollWidth > innerWidth, document.documentElement.scrollHeight > innerHeight]);
      if (hs[0] || hs[1]) note('page scroll', vp.join('x') + ' ' + JSON.stringify(hs));
      await context.close();
    }
    // Reduced motion: every scene played through from its first beat.
    {
      const { context, page } = await open(browser, { reduced: true });
      for (let scene = 1; scene <= 7; scene++) {
        await goto(page, '?mode=recording&scene=' + scene + '&beat=0');
        await page.keyboard.press('r');
        const beats = await page.evaluate(() => Pitch.controller.snapshot().beatCount);
        for (let b = 1; b < beats; b++) { await page.waitForTimeout(1300); await page.keyboard.press('Space'); }
        await page.waitForTimeout(1400);
        await audit(page, 'reduced ' + scene);
        await shot(page, path.join(OUT, 'reduced-motion'), 'scene-' + scene + '-ending');
      }
      await context.close();
    }
    // Rehearsal overlay and recording cleanliness.
    {
      const { context, page } = await open(browser, {});
      await goto(page, '?mode=rehearsal&scene=4&beat=5');
      await page.keyboard.press('n');
      await page.waitForTimeout(600);
      await shot(page, path.join(OUT, 'modes'), 'rehearsal-overlay-notes');
      await goto(page, '?mode=recording&scene=4&beat=5');
      for (const k of ['t', 'n', 'h', 'e']) await page.keyboard.press(k);
      await page.waitForTimeout(400);
      const leaked = await page.evaluate(() => Array.from(document.querySelectorAll('#overlay-root .ov')).filter(e => !e.hidden).length + document.querySelectorAll('#overlay-root .ov').length);
      if (leaked) note('recording overlay', leaked + ' overlay elements exist in recording mode');
      await page.mouse.move(500, 500);
      await page.waitForTimeout(1900);
      const hidden = await page.evaluate(() => document.documentElement.classList.contains('cursor-hidden'));
      if (!hidden) note('recording cursor', 'cursor not hidden after inactivity');
      await shot(page, path.join(OUT, 'modes'), 'recording-clean');
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(500);
      await shot(page, path.join(OUT, 'modes'), 'mobile-preview-390x844');
      await context.close();
    }
    // Loops, pause and restart determinism.
    {
      const { context, page } = await open(browser, {});
      await goto(page, '?scene=4&beat=6');
      await page.waitForTimeout(400);
      await page.keyboard.press('ArrowRight');
      await page.waitForTimeout(1400);
      const counts = await page.evaluate(() => Pitch.timeline.debugCounts());
      if (Object.keys(counts).some(k => k.startsWith('demo:'))) note('leaked jobs', JSON.stringify(counts));
      const hiddenAnims = await page.evaluate(() => document.getAnimations().filter(a => { const t = a.effect && a.effect.target; return t && t.closest && t.closest('.scene') && !t.closest('.scene.is-active'); }).length);
      if (hiddenAnims) note('hidden animations', hiddenAnims + ' running in hidden scenes');
      await goto(page, '?scene=4&beat=4');
      await page.waitForTimeout(400);
      await page.keyboard.press('Space');
      await page.waitForTimeout(2600);
      await page.keyboard.press('p');
      const a = await page.screenshot();
      await page.waitForTimeout(1200);
      const b = await page.screenshot();
      // Compare with a tolerance: re-rasterizing a filtered layer may shift a few pixels by a few
      // levels; real motion changes thousands of pixels by large amounts.
      const diff = await (async () => {
        const blank = await context.newPage();
        const r = await blank.evaluate(async (urls) => {
          const load = u => new Promise(res => { const i = new Image(); i.onload = () => res(i); i.src = u; });
          const [ia, ib] = await Promise.all(urls.map(load));
          const c = document.createElement('canvas'); c.width = ia.width; c.height = ia.height;
          const g = c.getContext('2d');
          g.drawImage(ia, 0, 0); const da = g.getImageData(0, 0, c.width, c.height).data;
          g.drawImage(ib, 0, 0); const db = g.getImageData(0, 0, c.width, c.height).data;
          let max = 0, big = 0;
          for (let i = 0; i < da.length; i += 4) {
            const d = Math.max(Math.abs(da[i] - db[i]), Math.abs(da[i + 1] - db[i + 1]), Math.abs(da[i + 2] - db[i + 2]));
            if (d > max) max = d;
            if (d > 8) big++;
          }
          return { max: max, big: big, total: da.length / 4 };
        }, ['data:image/png;base64,' + a.toString('base64'), 'data:image/png;base64,' + b.toString('base64')]);
        await blank.close();
        return r;
      })();
      if (diff.max > 24 || diff.big > diff.total * 0.0005) note('pause', 'frames differ while paused ' + JSON.stringify(diff));
      await page.keyboard.press('p');
      for (let i = 0; i < 6; i++) { await page.waitForTimeout(500); await page.keyboard.press('r'); }
      await page.waitForTimeout(800);
      await context.close();
    }
    if (!QUICK) await contactSheet(browser, entries);
  } finally {
    await browser.close();
    server.close();
  }
  const uniq = Array.from(new Set(problems));
  console.log(uniq.length ? 'PROBLEMS (' + uniq.length + '):\n- ' + uniq.join('\n- ') : 'All automated checks passed.');
  process.exitCode = uniq.length ? 1 : 0;
})().catch(e => { console.error(e); process.exit(2); });
