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
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');

/* Zero-dependency CDP fallback for machines with Node 22+ and Chrome/Edge but no playwright package. */
function loadChromium() {
  try { return require('playwright').chromium; } catch (_) {}
  const candidates = [
    process.env.CHROME_BIN,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
  ].filter(Boolean);
  const bin = candidates.find(p => fs.existsSync(p));
  if (!bin) throw new Error('Neither playwright nor a local Chrome/Edge binary was found.');

  return {
    async launch() {
      const userDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pitch-cdp-'));
      const proc = spawn(bin, [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
        '--disable-backgrounding-occluded-windows', '--mute-audio', '--hide-scrollbars',
        '--remote-debugging-port=0', '--user-data-dir=' + userDir, 'about:blank'
      ], { stdio: ['ignore', 'ignore', 'pipe'] });
      const wsUrl = await new Promise((resolve, reject) => {
        let buf = '';
        const t = setTimeout(() => reject(new Error('CDP startup timeout')), 15000);
        proc.stderr.on('data', d => {
          buf += d.toString();
          const m = /DevTools listening on (ws:\/\/[^\s]+)/.exec(buf);
          if (m) { clearTimeout(t); resolve(m[1]); }
        });
        proc.on('exit', code => { clearTimeout(t); reject(new Error('Browser exited ' + code)); });
      });
      const ws = new WebSocket(wsUrl);
      await new Promise((res, rej) => { ws.addEventListener('open', res, { once: true }); ws.addEventListener('error', rej, { once: true }); });
      let seq = 0;
      const pending = new Map(), listeners = new Map();
      ws.addEventListener('message', ev => {
        const msg = JSON.parse(typeof ev.data === 'string' ? ev.data : ev.data.toString());
        if (msg.id) {
          const p = pending.get(msg.id);
          if (p) { pending.delete(msg.id); if (msg.error) p.rej(new Error(msg.error.message)); else p.res(msg.result); }
        } else if (msg.method) {
          const key = (msg.sessionId || '') + ':' + msg.method;
          (listeners.get(key) || []).forEach(fn => fn(msg.params || {}));
        }
      });
      function send(method, params, sessionId) {
        return new Promise((res, rej) => {
          const id = ++seq;
          pending.set(id, { res, rej });
          const payload = { id, method, params: params || {} };
          if (sessionId) payload.sessionId = sessionId;
          ws.send(JSON.stringify(payload));
        });
      }
      function onEvent(sessionId, method, fn) {
        const key = (sessionId || '') + ':' + method;
        if (!listeners.has(key)) listeners.set(key, []);
        listeners.get(key).push(fn);
      }

      return {
        async newContext(cOpts) {
          cOpts = cOpts || {};
          const vp = cOpts.viewport || { width: 1920, height: 1080 };
          const dpr = cOpts.deviceScaleFactor || 1;
          const reduced = cOpts.reducedMotion === 'reduce';
          const { browserContextId } = await send('Target.createBrowserContext');
          const pages = [];
          return {
            async newPage() {
              const { targetId } = await send('Target.createTarget', { url: 'about:blank', browserContextId });
              const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
              const evs = { console: [], pageerror: [], request: [], response: [], requestfailed: [] };
              let curVp = { width: vp.width, height: vp.height };
              await send('Page.enable', {}, sessionId);
              await send('Runtime.enable', {}, sessionId);
              await send('Network.enable', {}, sessionId);
              await send('Emulation.setDeviceMetricsOverride', { width: vp.width, height: vp.height, deviceScaleFactor: dpr, mobile: false }, sessionId);
              await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: reduced ? 'reduce' : 'no-preference' }] }, sessionId);

              onEvent(sessionId, 'Runtime.consoleAPICalled', p => {
                const text = (p.args || []).map(a => a.value !== undefined ? a.value : (a.description || '')).join(' ');
                evs.console.forEach(fn => fn({ type: () => p.type, text: () => text }));
              });
              onEvent(sessionId, 'Runtime.exceptionThrown', p => {
                const d = p.exceptionDetails || {};
                const msg = (d.exception && d.exception.description) || d.text || 'exception';
                evs.pageerror.forEach(fn => fn({ message: msg }));
              });
              onEvent(sessionId, 'Network.requestWillBeSent', p => {
                if (p.request && p.request.url) evs.request.forEach(fn => fn({ url: () => p.request.url }));
              });
              onEvent(sessionId, 'Network.responseReceived', p => {
                if (p.response) evs.response.forEach(fn => fn({ status: () => p.response.status, url: () => p.response.url }));
              });
              onEvent(sessionId, 'Network.loadingFailed', p => {
                if (!p.canceled) evs.requestfailed.forEach(fn => fn({ url: () => p.errorText || 'failed' }));
              });

              const page = {
                on(name, fn) { if (evs[name]) evs[name].push(fn); },
                async goto(url) {
                  const loaded = new Promise(res => {
                    const h = () => { const arr = listeners.get(sessionId + ':Page.loadEventFired') || []; const idx = arr.indexOf(h); if (idx >= 0) arr.splice(idx, 1); res(); };
                    onEvent(sessionId, 'Page.loadEventFired', h);
                  });
                  await send('Page.navigate', { url }, sessionId);
                  await Promise.race([loaded, new Promise(r => setTimeout(r, 10000))]);
                },
                async waitForTimeout(ms) { await new Promise(r => setTimeout(r, ms)); },
                async evaluate(fn, arg) {
                  const expr = '(' + fn.toString() + ')(' + (arg === undefined ? '' : JSON.stringify(arg)) + ')';
                  const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true }, sessionId);
                  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text || 'evaluate error');
                  return r.result ? r.result.value : undefined;
                },
                async waitForFunction(fn, arg, opts) {
                  const deadline = Date.now() + ((opts && opts.timeout) || 10000);
                  while (Date.now() < deadline) {
                    try { if (await page.evaluate(fn, arg)) return; } catch (_) {}
                    await page.waitForTimeout(60);
                  }
                  throw new Error('waitForFunction timeout');
                },
                async setViewportSize(sz) {
                  curVp = { width: sz.width, height: sz.height };
                  await send('Emulation.setDeviceMetricsOverride', { width: sz.width, height: sz.height, deviceScaleFactor: dpr, mobile: false }, sessionId);
                },
                async screenshot(sOpts) {
                  sOpts = sOpts || {};
                  const format = sOpts.type === 'jpeg' ? 'jpeg' : 'png';
                  if (sOpts.fullPage) {
                    const size = await page.evaluate(() => ({ w: Math.max(document.documentElement.scrollWidth, innerWidth), h: Math.max(document.documentElement.scrollHeight, innerHeight) }));
                    await send('Emulation.setDeviceMetricsOverride', { width: size.w, height: size.h, deviceScaleFactor: dpr, mobile: false }, sessionId);
                    await page.waitForTimeout(120);
                  }
                  const params = { format, fromSurface: true };
                  if (format === 'jpeg' && sOpts.quality) params.quality = sOpts.quality;
                  const { data } = await send('Page.captureScreenshot', params, sessionId);
                  if (sOpts.fullPage) {
                    await send('Emulation.setDeviceMetricsOverride', { width: curVp.width, height: curVp.height, deviceScaleFactor: dpr, mobile: false }, sessionId);
                  }
                  const buf = Buffer.from(data, 'base64');
                  if (sOpts.path) fs.writeFileSync(sOpts.path, buf);
                  return buf;
                },
                keyboard: {
                  async press(key) {
                    await page.evaluate(k => {
                      const map = {
                        Space: { key: ' ', code: 'Space' },
                        ArrowRight: { key: 'ArrowRight', code: 'ArrowRight' },
                        ArrowLeft: { key: 'ArrowLeft', code: 'ArrowLeft' },
                        Escape: { key: 'Escape', code: 'Escape' }
                      };
                      const info = map[k] || { key: k, code: 'Key' + k.toUpperCase() };
                      window.dispatchEvent(new KeyboardEvent('keydown', { key: info.key, code: info.code, bubbles: true, cancelable: true }));
                      window.dispatchEvent(new KeyboardEvent('keyup', { key: info.key, code: info.code, bubbles: true, cancelable: true }));
                    }, key);
                  }
                },
                mouse: {
                  async move(x, y) {
                    await page.evaluate(pt => {
                      window.dispatchEvent(new PointerEvent('pointermove', { clientX: pt.x, clientY: pt.y, bubbles: true }));
                    }, { x, y });
                  }
                },
                async close() {
                  const idx = pages.indexOf(targetId);
                  if (idx >= 0) pages.splice(idx, 1);
                  try { await send('Target.closeTarget', { targetId }); } catch (_) {}
                }
              };
              pages.push(targetId);
              return page;
            },
            async close() {
              for (const t of pages) { try { await send('Target.closeTarget', { targetId: t }); } catch (_) {} }
              try { await send('Target.disposeBrowserContext', { browserContextId }); } catch (_) {}
            }
          };
        },
        async close() {
          try { ws.close(); } catch (_) {}
          try { proc.kill(); } catch (_) {}
          setTimeout(() => { try { fs.rmSync(userDir, { recursive: true, force: true }); } catch (_) {} }, 500);
        }
      };
    }
  };
}
const chromium = loadChromium();

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
  [4, 'strongest', 'Demo · Quick Focus wird Cosmic Core', { from: 4, wait: 3800 }],
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
  if (setup.beat != null) { await goto(page, '?' + m + 'scene=' + scene + '&beat=' + setup.beat); await page.waitForTimeout(450); return; }
  if (setup.from === -1) {
    await goto(page, '?' + m + 'scene=' + scene + '&beat=0');
    await page.keyboard.press('r');
  } else {
    await goto(page, '?' + m + 'scene=' + scene + '&beat=' + setup.from);
    await page.waitForTimeout(350);
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
  const html = '<!DOCTYPE html><meta charset="utf-8"><link rel="icon" href="data:,"><style>body{margin:0;padding:28px;background:#1D1A17;font:600 15px Inter,Arial,sans-serif;color:#F6F1E7}h1{margin:0 0 18px;font-size:24px}' +
    '.g{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}figure{margin:0}img{display:block;width:100%;border-radius:8px}figcaption{margin-top:6px;color:#CFC3E6}b{color:#fff}</style>' +
    '<h1>Focus Mode+ · Pitch review · 1920 × 1080 · ' + new Date().toISOString().slice(0, 10) + '</h1><div class="g">' + cells + '</div>';
  const file = path.join(OUT, 'contact-sheet.html');
  fs.writeFileSync(file, html);
  const ctx = await browser.newContext({ viewport: { width: 2400, height: 1200 }, deviceScaleFactor: 1 });
  const p = await ctx.newPage();
  await p.goto('http://127.0.0.1:' + PORT + '/review/contact-sheet.html');
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
  process.exit(uniq.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
