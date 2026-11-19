'use strict';
/*
 * Browser security regression tests (Playwright + Chromium, mobile viewport).
 *
 * Run:   node tests/security/browser-security.test.js
 * Env:   FM_BASE_URL         use an already running static server (default: spawn python3 http.server)
 *        FM_PORT             port for the spawned server (default 8791)
 *        FM_TAILWIND_STANDIN path to a local copy of the Tailwind 3.x Play CDN script, served in place of
 *                            https://cdn.tailwindcss.com (that host is often unreachable in CI sandboxes).
 *                            Without it a no-op stub is served, so styling is not exercised.
 *
 * The AI Worker is never contacted: every workers.dev request is fulfilled by a local mock.
 */
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { chromium } = require('playwright');
const { ROOT, createRunner, assert, assertEqual } = require('./lib/harness');

const runner = createRunner('Browser security (Playwright, 390x844)');
const test = runner.test;

const PORT = Number(process.env.FM_PORT || 8791);
const BASE = (process.env.FM_BASE_URL || `http://127.0.0.1:${PORT}`).replace(/\/$/, '');
const APP = BASE + '/index.html';
const WORKER = 'https://focusmodedemoai.illia-pohukai.workers.dev';
const TAILWIND = process.env.FM_TAILWIND_STANDIN ? fs.readFileSync(process.env.FM_TAILWIND_STANDIN, 'utf8') : '/* tailwind stub for tests */';
const APP_KEY = /^(focus-mode|focusmode-|focus-pwa-|focus_lang)/;
const BENIGN_CONSOLE = [/cdn\.tailwindcss\.com should not be used in production/];

let browser;
let server;

async function startServer() {
  if (process.env.FM_BASE_URL) return;
  server = spawn('python3', ['-m', 'http.server', String(PORT), '--bind', '127.0.0.1'], { cwd: ROOT, stdio: 'ignore' });
  for (let i = 0; i < 50; i += 1) {
    try {
      const res = await fetch(APP);
      if (res.ok) return;
    } catch (_) { /* retry */ }
    await new Promise(r => setTimeout(r, 100));
  }
  throw new Error('static server did not start');
}

async function newApp(options = {}) {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 1,
    isMobile: true,
    hasTouch: true,
    locale: 'de-DE',
    acceptDownloads: true,
    serviceWorkers: options.serviceWorkers || 'allow'
  });
  const thirdParty = [];
  await context.route('https://cdn.tailwindcss.com/**', r => r.fulfill({ status: 200, contentType: 'application/javascript', body: TAILWIND }));
  await context.route(url => !url.href.startsWith(BASE) && !url.href.startsWith('https://cdn.tailwindcss.com') && !url.href.startsWith(WORKER), r => {
    thirdParty.push(r.request().url());
    return r.abort();
  });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('console', m => {
    if (m.type() !== 'error') return;
    const text = m.text();
    if (!BENIGN_CONSOLE.some(re => re.test(text))) consoleErrors.push(text.slice(0, 300));
  });
  page.on('pageerror', e => consoleErrors.push('pageerror: ' + e.message.slice(0, 300)));
  await page.addInitScript(() => {
    window.__cspViolations = [];
    document.addEventListener('securitypolicyviolation', e => window.__cspViolations.push(e.violatedDirective + ' ' + e.blockedURI));
  });
  await page.goto(APP, { waitUntil: 'load' });
  await page.waitForFunction(() => window.FocusModeApp && typeof window.openDataManagementScreen === 'function');
  await page.waitForTimeout(400);
  return { context, page, consoleErrors, thirdParty };
}

function maliciousBackup() {
  const mid = 'mission_77777777-7777-4777-8777-777777777777';
  const steps = [0, 1].map(i => ({
    id: `step_${i}_xss`, missionId: mid, title: `<img src=x onerror="window.__xss='step${i}'">Schritt`,
    task: '<script>window.__xss="task"</script> javascript:alert(1)', durationMinutes: 25,
    status: i === 0 ? 'active' : 'pending', reason: '<b onmouseover="window.__xss=1">Grund</b>',
    requiredTools: ['<svg onload="window.__xss=2">'], recommendedApps: [], createdAt: 1790000000000, updatedAt: 1790000000000, completedAt: null
  }));
  return {
    exportVersion: 1, applicationVersion: 'v16 Data Stability', exportedAt: '2026-11-01T10:00:00.000Z',
    schemaVersions: { bundle: 1, missions: 1, missionSteps: 1, sessions: 1, sessionHistory: 1, preferences: 1, recovery: 1 },
    data: {
      missions: {
        schemaVersion: 1, stepSchemaVersion: 1, selectedMissionId: mid, primaryMissionId: mid, updatedAt: 1790000000000,
        missions: [{
          id: mid, title: '<img src=x onerror="window.__xss=\'title\'">Klausur', subject: '<i>Bio</i>', type: 'exam',
          deadlineText: '"><script>window.__xss=3</script>', status: 'active', createdAt: 1790000000000, updatedAt: 1790000000000,
          currentStepId: steps[0].id, estimate: { explanation: '<a href="javascript:alert(1)">x</a>' }, sourceAnalysis: {}, steps,
          progress: { completedStepIds: [], history: [], initialStepCount: 2 }
        }]
      },
      sessionHistory: { schemaVersion: 1, sessions: [{
        id: 'session_xss_1', type: 'mission', status: 'completed', title: '<img src=x onerror="window.__xss=\'hist\'">', task: 't',
        plannedMinutes: 25, actualMinutes: 25, startedAt: 1790000000000, finishedAt: 1790001500000,
        missionId: mid, missionStepId: steps[0].id, result: { outcome: 'completed', feedback: '<script>window.__xss=4</script>' }
      }] },
      activeSession: null, lastSession: null, preferences: { schemaVersion: 1, language: 'de' }
    }
  };
}

async function importViaUI(page, name, content) {
  await page.evaluate(() => window.openDataManagementScreen());
  await page.setInputFiles('#dataImportFileInput', { name, mimeType: 'application/json', buffer: Buffer.from(content) });
  await page.waitForFunction(() => {
    const card = document.getElementById('dataImportPreviewCard');
    const banner = document.getElementById('dataFeedbackBanner');
    return (card && !card.classList.contains('hidden')) || (banner && /dm-feedback-error/.test(banner.className));
  }, null, { timeout: 10000 });
}

async function assertNoXss(page, label) {
  const state = await page.evaluate(() => ({
    xss: window.__xss,
    injectedImg: document.querySelectorAll('img[src="x"]').length,
    injectedScripts: [...document.querySelectorAll('script')].filter(s => /__xss/.test(s.textContent)).length,
    handlerAttrs: [...document.querySelectorAll('*')].flatMap(el => [...el.attributes].filter(a => /^on/i.test(a.name) && /__xss|alert\(/.test(a.value)).map(a => a.name)).length,
    jsLinks: document.querySelectorAll('a[href^="javascript:"]').length
  }));
  assertEqual(state.xss, undefined, label + ': no script executed');
  assertEqual(state.injectedImg + state.injectedScripts + state.handlerAttrs + state.jsLinks, 0, label + ': no injected markup ' + JSON.stringify(state));
}

/* ---------------------------------------------------------------- */

test('app loads with no console errors, no CSP violations and no unexpected third-party requests', async () => {
  const { context, page, consoleErrors, thirdParty } = await newApp();
  await page.waitForTimeout(1200);
  const info = await page.evaluate(() => ({
    csp: window.__cspViolations,
    cspMeta: document.querySelector('meta[http-equiv="Content-Security-Policy"]')?.content || '',
    icons: document.querySelectorAll('svg.lucide').length,
    fontFaces: [...document.fonts].map(f => f.status),
    homeVisible: !document.getElementById('app-screen-home').classList.contains('hidden')
  }));
  assert(info.homeVisible, 'home visible');
  assert(info.cspMeta.includes("object-src 'none'") && !info.cspMeta.includes('unsafe-eval'), 'CSP present without unsafe-eval');
  assertEqual(info.csp.length, 0, 'CSP violations: ' + JSON.stringify(info.csp));
  assert(info.icons > 0, 'vendored Lucide renders icons');
  assert(info.fontFaces.includes('loaded'), 'self-hosted Inter loaded');
  assertEqual(consoleErrors.length, 0, 'console errors: ' + JSON.stringify(consoleErrors));
  assertEqual(thirdParty.length, 0, 'third-party requests: ' + JSON.stringify(thirdParty));
  await context.close();
});

test('service worker precaches the app without docs, tests, .claude, Worker or third-party font/icon URLs; offline reload works', async () => {
  const { context, page } = await newApp();
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.reload({ waitUntil: 'load' });
  await page.waitForFunction(() => Boolean(navigator.serviceWorker.controller));
  const cache = await page.evaluate(async () => {
    const keys = await caches.keys();
    const urls = [];
    for (const k of keys) {
      const c = await caches.open(k);
      urls.push(...(await c.keys()).map(r => r.url));
    }
    return { keys, urls };
  });
  assertEqual(cache.keys.length, 1, 'one cache: ' + cache.keys);
  assert(/^focus-mode-v17-17-/.test(cache.keys[0]), 'versioned cache name');
  assert(cache.urls.some(u => u.endsWith('/assets/vendor/lucide/lucide.min.js')), 'vendored lucide cached');
  const bad = cache.urls.filter(u => /\/(docs|tests|\.claude)\/|workers\.dev|fonts\.g|unpkg|\.md$/.test(u));
  assertEqual(bad.length, 0, 'unexpected cached URLs: ' + JSON.stringify(bad));
  // Request a doc file directly: it must not end up in the cache.
  await page.evaluate(() => fetch('./docs/security/threat-model.md').then(r => r.text()).catch(() => ''));
  const docCached = await page.evaluate(async () => Boolean(await caches.match('./docs/security/threat-model.md')));
  assert(!docCached, 'docs never cached at runtime');
  await context.setOffline(true);
  await page.reload({ waitUntil: 'load' });
  await page.waitForFunction(() => window.FocusModeApp && !document.getElementById('app-screen-home').classList.contains('hidden'), null, { timeout: 10000 });
  await context.setOffline(false);
  await context.close();
});

test('hostile backup: preview shown, import confirmed, all HTML stays text across screens', async () => {
  const { context, page, consoleErrors } = await newApp();
  await importViaUI(page, 'shared-backup.json', JSON.stringify(maliciousBackup()));
  const preview = await page.evaluate(() => ({
    visible: !document.getElementById('dataImportPreviewCard').classList.contains('hidden'),
    integrity: document.getElementById('importPreviewIntegrity')?.textContent,
    trust: document.getElementById('dataImportTrustNote')?.textContent,
    warning: document.getElementById('dataImportWarningText')?.textContent
  }));
  assert(preview.visible, 'preview visible');
  assertEqual(preview.integrity, 'Älteres Backup ohne Prüfsumme', 'integrity label for file without digest');
  assert(/keine verifizierten Nachweise/.test(preview.trust), 'local-backup disclaimer');
  assert(/ersetzt/.test(preview.warning) && /Backup/.test(preview.warning), 'replace + backup warning');
  await page.click('#confirmImportBtn');
  await page.waitForFunction(() => /dm-feedback-success/.test(document.getElementById('dataFeedbackBanner').className));
  await assertNoXss(page, 'data screen');
  for (const screen of ['home', 'plan', 'history', 'stats']) {
    await page.evaluate(s => window.setAppScreen(s, { immediate: true }), screen);
    await page.waitForTimeout(300);
    await assertNoXss(page, screen);
  }
  const titleShown = await page.evaluate(() => document.body.textContent.includes('<img src=x onerror="window.__xss=\'title\'">Klausur'));
  assert(titleShown, 'hostile title rendered literally as text');
  assertEqual(consoleErrors.length, 0, 'console errors: ' + JSON.stringify(consoleErrors));
  await context.close();
});

test('import rejections show concise German messages and leave data unchanged', async () => {
  const { context, page } = await newApp();
  const before = await page.evaluate(() => localStorage.getItem('focus-mode-missions-v11-5'));
  const cases = [
    ['broken.json', '{"exportVersion":1,', /Datei beschädigt: kein gültiges JSON/],
    ['big.json', '{"pad":"' + 'x'.repeat(5 * 1024 * 1024 + 100) + '"}', /zu groß \(max\. 5 MB\)/],
    ['proto.json', '{"exportVersion":1,"data":{"missions":{"missions":[]},"sessionHistory":{"sessions":[]},"preferences":{"__proto__":{"x":1}}}}', /unzulässige Feldnamen/],
    ['other.json', JSON.stringify({ format: 'something-else', data: {} }), /Format nicht unterstützt/]
  ];
  for (const [name, content, expected] of cases) {
    await importViaUI(page, name, content);
    const banner = await page.evaluate(() => document.getElementById('dataFeedbackBanner').textContent);
    assert(expected.test(banner), name + ': ' + banner);
    assert(!/\bat\s+\S+\s+\(|Error:|\.js:\d|stack|_[a-z]+_/i.test(banner), name + ': no internal details: ' + banner);
  }
  const after = await page.evaluate(() => localStorage.getItem('focus-mode-missions-v11-5'));
  assertEqual(after, before, 'mission store unchanged');
  await context.close();
});

test('export via UI: safe filenames, SHA-256 digest, missions-only variant, tampered file rejected on import', async () => {
  const { context, page } = await newApp();
  await importViaUI(page, 'seed.json', fs.readFileSync(path.join(__dirname, 'fixtures', 'export-v17.16-full.json'), 'utf8'));
  await page.click('#confirmImportBtn');
  await page.waitForFunction(() => /dm-feedback-success/.test(document.getElementById('dataFeedbackBanner').className));
  const [full] = await Promise.all([page.waitForEvent('download'), page.click('#exportDataBtn')]);
  assert(/^focus-mode-backup-\d{4}-\d{2}-\d{2}\.json$/.test(full.suggestedFilename()), 'full filename ' + full.suggestedFilename());
  const fullJson = JSON.parse(fs.readFileSync(await full.path(), 'utf8'));
  assert(/^[0-9a-f]{64}$/.test(fullJson.integrity.digest), 'digest present');
  assertEqual(fullJson.data.sessionHistory.sessions.length, 12, 'history included');
  const [scoped] = await Promise.all([page.waitForEvent('download'), page.click('#exportMissionsOnlyBtn')]);
  assert(/^focus-mode-backup-missionen-\d{4}-\d{2}-\d{2}\.json$/.test(scoped.suggestedFilename()), 'scoped filename');
  const scopedJson = JSON.parse(fs.readFileSync(await scoped.path(), 'utf8'));
  assertEqual(scopedJson.data.sessionHistory.sessions.length, 0, 'no history in scoped export');
  const banner = await page.evaluate(() => document.getElementById('dataFeedbackBanner').textContent);
  assert(/Prüfsumme \(SHA-256\)/.test(banner), 'success banner mentions checksum: ' + banner);
  fullJson.data.missions.missions[0].title = 'verändert';
  await importViaUI(page, 'tampered.json', JSON.stringify(fullJson));
  const rejected = await page.evaluate(() => document.getElementById('dataFeedbackBanner').textContent);
  assert(/Prüfsumme stimmt nicht/.test(rejected), 'tampered file rejected: ' + rejected);
  // Re-import of the untampered original succeeds and shows "Backup geprüft".
  const original = fs.readFileSync(await full.path(), 'utf8');
  await importViaUI(page, 'original.json', original);
  const label = await page.evaluate(() => document.getElementById('importPreviewIntegrity')?.textContent);
  assertEqual(label, 'Backup geprüft (Datei unverändert)', 'verified label');
  await context.close();
});

test('"Alle lokalen Daten löschen" removes every app key including backups after explicit confirmation', async () => {
  const { context, page } = await newApp();
  await importViaUI(page, 'seed.json', fs.readFileSync(path.join(__dirname, 'fixtures', 'export-v17.16-full.json'), 'utf8'));
  await page.click('#confirmImportBtn');
  await page.waitForFunction(() => /dm-feedback-success/.test(document.getElementById('dataFeedbackBanner').className));
  const keysBefore = await page.evaluate(() => Object.keys(localStorage));
  assert(keysBefore.some(k => /backup/.test(k)), 'backup keys exist before');
  await page.click('#deleteAllLocalDataBtn');
  await page.waitForSelector('#dataDeleteAllConfirmCard:not(.hidden)');
  await page.click('#confirmDeleteAllDataBtn');
  await page.waitForFunction(() => /dm-feedback-success/.test(document.getElementById('dataFeedbackBanner').className));
  const left = await page.evaluate(() => Object.keys(localStorage));
  const leftApp = left.filter(k => APP_KEY.test(k));
  assertEqual(leftApp.filter(k => /backup|missions|history|session/.test(k)).length, 0, 'no user data or backup keys left: ' + JSON.stringify(leftApp));
  await context.close();
});

test('AI output with breakout IDs cannot inject handlers; repository and adaptation enforce the ID policy', async () => {
  const { context, page } = await newApp();
  const result = await page.evaluate(() => {
    const evil = "x');window.__xss='ai';('";
    pendingAIPlan = normalizeAIPlan({
      goal: { title: '<img src=x onerror="window.__xss=\'goal\'">Ziel', subject: 'Mathe', type: 'exam', deadlineText: '' },
      sessions: [
        { id: evil, title: '<svg onload="window.__xss=1">', task: '<script>window.__xss=2</script>', durationMinutes: 20 },
        { id: evil, title: 'Zwei', task: 'B', durationMinutes: 20 }
      ],
      sourceAnalysis: { facts: ['<img src=x onerror="window.__xss=3">'] }
    }, null);
    renderAIConfirmation(pendingAIPlan);
    confirmAIPlan();
    const repo = window.FocusModeApp.repositories.missions;
    const created = repo.create({ title: 'Direkt', steps: [{ id: "a');window.__xss='repo';('", title: 'S', durationMinutes: 20 }] });
    const adapt = window.FocusModeApp.core.getModule('features.optionalAdaptation').normalizeUpdatedSessions([{ id: evil, title: 'A', durationMinutes: 20 }, { id: 'keep-id', title: 'B' }]);
    const isValid = window.FocusModeApp.data.schema.isValidEntityId;
    const all = repo.getAll();
    return {
      stepIds: all.flatMap(m => m.steps.map(s => s.id)),
      allValid: all.every(m => isValid(m.id) && m.steps.every(s => isValid(s.id))),
      createdOk: created.ok,
      adaptIds: adapt.map(s => s.id),
      adaptValid: adapt.every(s => isValid(s.id))
    };
  });
  assert(result.allValid, 'all stored ids valid: ' + JSON.stringify(result.stepIds));
  assert(result.createdOk, 'direct create ok');
  assert(result.adaptValid && result.adaptIds[1] === 'keep-id', 'adaptation ids: ' + JSON.stringify(result.adaptIds));
  for (const screen of ['home', 'plan']) {
    await page.evaluate(s => window.setAppScreen(s, { immediate: true }), screen);
    await page.waitForTimeout(300);
    await assertNoXss(page, 'ai ' + screen);
  }
  await page.evaluate(() => window.openMissionManagementV116?.());
  await page.waitForTimeout(300);
  await assertNoXss(page, 'mission management');
  await context.close();
});

test('legacy stored step IDs with unsafe characters are remapped on the next repository update', async () => {
  const { context, page } = await newApp();
  const r = await page.evaluate(() => {
    const mid = 'mission_legacy01';
    const bad = 'Schritt 1 "alt"';
    const store = { schemaVersion: 1, missions: [{ id: mid, title: 'Alt', subject: 'x', type: 'custom', deadlineText: 'Nicht angegeben', status: 'active', createdAt: 1, updatedAt: 1, currentStepId: bad, estimate: {}, sourceAnalysis: {},
      steps: [{ id: bad, missionId: mid, title: 'S1', task: '', durationMinutes: 20, status: 'active' }, { id: 'ok_step', missionId: mid, title: 'S2', task: '', durationMinutes: 20, status: 'pending' }],
      progress: { completedStepIds: [], history: [], initialStepCount: 2 } }], selectedMissionId: mid, primaryMissionId: mid, updatedAt: 1 };
    localStorage.setItem('focus-mode-missions-v11-5', JSON.stringify(store));
    const updated = window.FocusModeApp.repositories.missions.update(mid, { subject: 'y' });
    return { ids: updated.steps.map(s => s.id), current: updated.currentStepId };
  });
  assert(r.ids[0] !== 'Schritt 1 "alt"' && /^[a-zA-Z0-9_.:-]+$/.test(r.ids[0]), 'unsafe id replaced');
  assertEqual(r.ids[1], 'ok_step', 'safe id untouched');
  assertEqual(r.current, r.ids[0], 'currentStepId follows remap');
  await context.close();
});

async function openGoalWithText(page, text) {
  await page.evaluate(() => (window.openGoalIntake ? window.openGoalIntake() : window.setAppScreen('goal', { immediate: true })));
  await page.fill('#goalPrompt', text);
  await page.evaluate(() => window.updateMissionPlanButton?.());
  await page.waitForTimeout(900); // let the screen transition settle
}

const VALID_PLAN = {
  ok: true,
  plan: { goal: { title: 'Biologie', subject: 'Bio', type: 'exam', deadlineText: '' }, sessions: [
    { id: 's1', title: 'Lesen', task: 'Kapitel 1', durationMinutes: 25 },
    { id: 's2', title: 'Üben', task: 'Aufgaben', durationMinutes: 25 }
  ], sourceAnalysis: {}, estimate: { totalMinutes: 50 } }
};

test('AI request: double tap sends one request; cancel + retry cannot release the newer lock', async () => {
  const { context, page } = await newApp({ serviceWorkers: 'block' });
  const requests = [];
  const pending = [];
  await context.route(WORKER + '/**', route => { requests.push(route.request()); pending.push(route); });
  await openGoalWithText(page, 'Biologie-Klausur in zwei Wochen');
  await page.evaluate(() => { analyzeGoal(); analyzeGoal(); analyzeGoal(); });
  await page.waitForTimeout(300);
  assertEqual(requests.length, 1, 'one request for a triple tap');
  await page.evaluate(() => { cancelMissionAnalysis(); analyzeGoal(); });
  await page.waitForTimeout(300);
  assertEqual(requests.length, 2, 'retry after cancel sends a new request');
  // Let the aborted first request settle late: it must not clear the in-flight lock of the second.
  await pending[0].fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(VALID_PLAN) }).catch(() => {});
  await page.waitForTimeout(200);
  assert(await page.evaluate(() => window.isMissionAnalyzeInFlight()), 'second request still owns the lock');
  await page.evaluate(() => analyzeGoal());
  await page.waitForTimeout(200);
  assertEqual(requests.length, 2, 'no third request while second is in flight');
  await pending[1].fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(VALID_PLAN) });
  await page.waitForFunction(() => typeof pendingAIPlan === 'object' && pendingAIPlan?.goal?.title === 'Biologie', null, { timeout: 5000 });
  const body = requests[1].postData() || '';
  assert(!/sessionHistory|focusmode-|streak|missions-v11/i.test(body), 'request carries no local history/streak/storage data');
  await context.close();
});

test('AI error: request ID shown as support reference, server HTML rendered as text, input preserved', async () => {
  const { context, page } = await newApp({ serviceWorkers: 'block' });
  await context.route(WORKER + '/**', route => route.fulfill({
    status: 429, contentType: 'application/json',
    body: JSON.stringify({ ok: false, error: { code: 'AI_RATE_LIMITED', message: '<img src=x onerror="window.__xss=\'err\'">Zu viele Anfragen', retryable: true, requestId: 'req_test42' } })
  }));
  await openGoalWithText(page, 'Mathe Hausaufgabe');
  await page.evaluate(() => analyzeGoal());
  await page.waitForFunction(() => !document.getElementById('missionError').classList.contains('hidden'), null, { timeout: 5000 });
  const err = await page.evaluate(() => document.getElementById('missionError').textContent);
  assert(err.includes('Anfrage-ID: req_test42'), 'request id shown: ' + err);
  assert(err.includes('<img src=x'), 'server text shown literally');
  await assertNoXss(page, 'ai error');
  assertEqual(await page.inputValue('#goalPrompt'), 'Mathe Hausaufgabe', 'input preserved');
  await context.close();
});

function pngHeader(w, h) {
  const b = Buffer.alloc(33);
  Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52]).copy(b);
  b.writeUInt32BE(w, 16);
  b.writeUInt32BE(h, 20);
  return b;
}

test('attachments: spoofed type and oversized dimensions rejected; EXIF/GPS and filename never uploaded', async () => {
  const { context, page } = await newApp({ serviceWorkers: 'block' });
  const uploads = [];
  await context.route(WORKER + '/**', route => { uploads.push(route.request().postDataBuffer()); route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(VALID_PLAN) }); });
  await openGoalWithText(page, 'Arbeitsblatt');
  const errorText = () => page.evaluate(() => document.getElementById('missionError').textContent);

  await page.setInputFiles('#missionFileInput', { name: 'foto.jpg', mimeType: 'image/jpeg', buffer: pngHeader(100, 100) });
  await page.waitForTimeout(300);
  assert(/Dateiinhalt passt nicht/.test(await errorText()), 'spoofed type rejected: ' + await errorText());

  await page.setInputFiles('#missionFileInput', { name: 'bomb.png', mimeType: 'image/png', buffer: pngHeader(30000, 30000) });
  await page.waitForTimeout(300);
  assert(/Bildauflösung ist zu groß/.test(await errorText()), 'bomb rejected: ' + await errorText());

  const jpegB64 = await page.evaluate(() => {
    const c = document.createElement('canvas'); c.width = 64; c.height = 48;
    const g = c.getContext('2d'); g.fillStyle = '#6D28D9'; g.fillRect(0, 0, 64, 48);
    return c.toDataURL('image/jpeg', 0.9).split(',')[1];
  });
  const jpeg = Buffer.from(jpegB64, 'base64');
  const marker = Buffer.from('Exif\u0000\u0000GPSLatitudeTEST-48.137');
  const app1 = Buffer.concat([Buffer.from([0xFF, 0xE1, (marker.length + 2) >> 8, (marker.length + 2) & 255]), marker]);
  const withExif = Buffer.concat([jpeg.subarray(0, 2), app1, jpeg.subarray(2)]);
  await page.setInputFiles('#missionFileInput', { name: 'Max_Mustermann_Zeugnis.jpg', mimeType: 'image/jpeg', buffer: withExif });
  await page.waitForFunction(() => window.hasMissionImageAttached(), null, { timeout: 5000 });
  const prepared = await page.evaluate(async () => {
    const bytes = new Uint8Array(await missionImageFile.arrayBuffer());
    const text = String.fromCharCode(...bytes);
    return { name: missionImageFile.name, type: missionImageFile.type, hasExif: text.includes('Exif'), hasGps: text.includes('GPSLatitudeTEST') };
  });
  assertEqual(prepared.name, 'material.jpg', 'generic name');
  assert(!prepared.hasExif && !prepared.hasGps, 'metadata stripped');
  await page.evaluate(() => analyzeGoal());
  await page.waitForTimeout(800);
  assertEqual(uploads.length, 1, 'one upload');
  const body = uploads[0].toString('latin1');
  assert(body.includes('filename="material.jpg"'), 'generic filename uploaded');
  assert(!body.includes('Mustermann') && !body.includes('GPSLatitudeTEST'), 'no personal filename or GPS metadata uploaded');
  await context.close();
});

test('goal screen explains what is sent to the AI without blocking', async () => {
  const { context, page } = await newApp({ serviceWorkers: 'block' });
  await openGoalWithText(page, 'x');
  const note = await page.evaluate(() => {
    const d = document.getElementById('missionAiPrivacyNote');
    return d ? { open: d.open, summary: d.querySelector('summary').textContent.trim(), text: d.textContent } : null;
  });
  assert(note && note.summary === 'Was wird an die AI gesendet?', 'note present');
  assert(!note.open, 'collapsed by default (non-obstructive)');
  assert(/bleiben auf diesem Gerät/.test(note.text) && /Google Gemini/.test(note.text), 'content');
  const reachable = await page.evaluate(() => {
    const btn = document.getElementById('missionPlanButton');
    btn.scrollIntoView({ block: 'center' });
    const r = btn.getBoundingClientRect();
    const hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
    return Boolean(hit && hit.closest('#missionPlanButton'));
  });
  assert(reachable, 'note does not cover the plan button');
  await context.close();
});

(async () => {
  let failed = 1;
  try {
    await startServer();
    browser = await chromium.launch();
    failed = await runner.run();
  } catch (error) {
    console.error(error);
  } finally {
    if (browser) await browser.close();
    if (server) server.kill();
    process.exitCode = failed ? 1 : 0;
  }
})();
