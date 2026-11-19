'use strict';
/*
 * Service-worker caching policy tests. Loads service-worker.js into a vm with a stub
 * ServiceWorkerGlobalScope and checks the pure routing decision plus CORE consistency.
 * Run: node tests/security/sw-policy.test.js
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { ROOT, createRunner, assert, assertEqual } = require('./lib/harness');

const runner = createRunner('Service-worker policy');
const test = runner.test;

const ORIGIN = 'https://example.github.io';
const SCOPE = ORIGIN + '/FocusModePWA/';

function loadSW() {
  const listeners = {};
  const sandbox = {
    URL,
    Request: class { constructor(url, init = {}) { this.url = url; this.mode = init.mode || 'cors'; this.method = 'GET'; } },
    Response: class {},
    setTimeout,
    clearTimeout,
    fetch: async () => { throw new Error('network disabled in test'); },
    caches: { open: async () => ({}), keys: async () => [], match: async () => undefined, delete: async () => true },
    console
  };
  sandbox.self = sandbox;
  sandbox.location = { href: SCOPE + 'service-worker.js', origin: ORIGIN };
  sandbox.registration = { scope: SCOPE };
  sandbox.addEventListener = (type, fn) => { listeners[type] = fn; };
  sandbox.clients = { claim: async () => {} };
  sandbox.skipWaiting = async () => {};
  vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(path.join(ROOT, 'service-worker.js'), 'utf8'), sandbox, { filename: 'service-worker.js' });
  return { policy: sandbox.FocusModeSWPolicy, listeners };
}

const req = (url, extra = {}) => ({ url, method: 'GET', mode: 'no-cors', ...extra });

test('AI Worker requests are never intercepted (any method, any path)', () => {
  const { policy } = loadSW();
  assertEqual(policy.classifyRequest(req('https://focusmodedemoai.illia-pohukai.workers.dev/create-mission', { method: 'POST' })), 'bypass', 'POST create');
  assertEqual(policy.classifyRequest(req('https://focusmodedemoai.illia-pohukai.workers.dev/health')), 'bypass', 'GET health');
  assertEqual(policy.classifyRequest(req(SCOPE + 'adapt-mission')), 'bypass', 'same-origin API path');
  assertEqual(policy.classifyRequest(req(SCOPE + 'index.html', { method: 'POST' })), 'bypass', 'non-GET');
});

test('development material is never cached', () => {
  const { policy } = loadSW();
  for (const p of ['docs/security/security-audit-v17.17.md', '.claude/skills/ui-ux-pro-max/SKILL.md', 'tests/security/fixtures/export-v17.16-full.json', 'CHANGELOG.md', 'src/core/navigation.js.map', '.git/config']) {
    assertEqual(policy.classifyRequest(req(SCOPE + p)), 'bypass', p);
  }
});

test('static same-origin assets are cacheable; other same-origin requests are network-only', () => {
  const { policy } = loadSW();
  assertEqual(policy.classifyRequest(req(SCOPE + 'src/core/navigation.js')), 'same-origin-asset', 'js');
  assertEqual(policy.classifyRequest(req(SCOPE + 'assets/fonts/inter/inter-latin-wght-normal.woff2')), 'same-origin-asset', 'font');
  assertEqual(policy.classifyRequest(req(SCOPE + 'some/endpoint')), 'same-origin-network', 'unknown same-origin path');
  assertEqual(policy.classifyRequest(req(SCOPE + 'index.html', { mode: 'navigate' })), 'navigate', 'navigation');
});

test('only the Tailwind CDN is cacheable cross-origin; fonts/unpkg/others pass through', () => {
  const { policy } = loadSW();
  assertEqual(policy.classifyRequest(req('https://cdn.tailwindcss.com/')), 'cdn', 'tailwind');
  for (const u of ['https://fonts.googleapis.com/css2?family=Inter', 'https://unpkg.com/lucide@latest', 'https://evil.example/x.js', 'https://example.com/a.png']) {
    assertEqual(policy.classifyRequest(req(u)), 'bypass', u);
  }
  assertEqual(policy.classifyRequest(req('chrome-extension://abc/x.js')), 'bypass', 'extension scheme');
  assertEqual(policy.classifyRequest(req('https://other.example/', { mode: 'navigate' })), 'bypass', 'cross-origin navigation');
});

test('failed, opaque, redirected-HTML and non-basic responses are not cacheable as assets', () => {
  const { policy } = loadSW();
  const res = (status, type, ct) => ({ ok: status >= 200 && status < 300, status, type, headers: { get: () => ct } });
  assert(policy.isCacheableSameOriginResponse(res(200, 'basic', 'application/javascript')), 'normal js cacheable');
  assert(!policy.isCacheableSameOriginResponse(res(404, 'basic', 'text/html')), '404');
  assert(!policy.isCacheableSameOriginResponse(res(500, 'basic', 'application/javascript')), '500');
  assert(!policy.isCacheableSameOriginResponse(res(0, 'opaque', '')), 'opaque');
  assert(!policy.isCacheableSameOriginResponse(res(200, 'basic', 'text/html; charset=utf-8')), 'html fallback for asset');
  assert(policy.isAppShellPath(new URL(SCOPE + 'index.html?v=11.8.1')), 'shell with query');
  assert(!policy.isAppShellPath(new URL(SCOPE + 'docs/index.html')), 'docs page is not the shell');
});

test('CORE precache: versioned name, every runtime script/style present, files exist, no dev material', () => {
  const { policy } = loadSW();
  assert(/^focus-mode-v17-17-/.test(policy.CACHE), 'cache name bumped: ' + policy.CACHE);
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const refs = [...html.matchAll(/<(?:script[^>]*\ssrc|link[^>]*\shref)="(\.\/[^"?#]+)"/g)].map(m => m[1]);
  for (const ref of refs) {
    assert(policy.CORE.includes(ref), 'CORE contains ' + ref);
  }
  for (const entry of policy.CORE) {
    if (entry === './') continue;
    assert(fs.existsSync(path.join(ROOT, entry)), 'file exists ' + entry);
    assert(!/(^|\/)(docs|tests|\.claude|scratchpad)\//.test(entry) && !/\.md$/.test(entry), 'no dev material ' + entry);
  }
  assertEqual(JSON.stringify(policy.EXTERNAL_CDN_URLS), '["https://cdn.tailwindcss.com"]', 'only Tailwind warmed');
});

module.exports = runner;
if (require.main === module) {
  runner.run().then(failed => { process.exitCode = failed ? 1 : 0; });
}
