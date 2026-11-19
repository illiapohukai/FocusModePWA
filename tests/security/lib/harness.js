'use strict';
/*
 * Focus Mode+ security test harness (Node, no dependencies).
 *
 * Loads the browser data modules (classic IIFE scripts that expect `window`)
 * into an isolated vm context with an in-memory localStorage shim, so the
 * real import/export/validator/migration code can be exercised without a
 * browser. This is test infrastructure only; it is never shipped or cached.
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.resolve(__dirname, '..', '..', '..');

const DATA_MODULES = [
  'src/core/architecture-shell.js',
  'src/services/storage-service.js',
  'src/data/schema-versions.js',
  'src/data/data-validator.js',
  'src/data/migration-runner.js',
  'src/data/data-export.js',
  'src/data/data-import.js',
  'src/data/data-reset.js'
];

function createLocalStorage(options = {}) {
  const map = new Map();
  const state = { quotaBytes: options.quotaBytes || Infinity, failKeys: new Set(), writes: 0 };
  function usedBytes(extraKey, extraValue) {
    let total = 0;
    for (const [k, v] of map) {
      if (k === extraKey) continue;
      total += (k.length + v.length) * 2;
    }
    if (extraKey != null) total += (extraKey.length + String(extraValue).length) * 2;
    return total;
  }
  const storage = {
    get length() { return map.size; },
    key(i) { return Array.from(map.keys())[i] ?? null; },
    getItem(k) { k = String(k); return map.has(k) ? map.get(k) : null; },
    setItem(k, v) {
      k = String(k); v = String(v);
      if (state.failKeys.has(k) || usedBytes(k, v) > state.quotaBytes) {
        const err = new Error('The quota has been exceeded.');
        err.name = 'QuotaExceededError';
        err.code = 22;
        throw err;
      }
      state.writes += 1;
      map.set(k, v);
    },
    removeItem(k) { map.delete(String(k)); },
    clear() { map.clear(); }
  };
  return { storage, map, state };
}

function createContext(options = {}) {
  const ls = createLocalStorage(options);
  const logs = [];
  const quietConsole = {
    log: (...a) => logs.push(['log', a]),
    info: (...a) => logs.push(['info', a]),
    warn: (...a) => logs.push(['warn', a]),
    error: (...a) => logs.push(['error', a]),
    debug: () => {}
  };
  const sandbox = {
    console: options.verbose ? console : quietConsole,
    localStorage: ls.storage,
    structuredClone,
    crypto: options.noCrypto ? undefined : globalThis.crypto,
    TextEncoder,
    TextDecoder,
    URL,
    Blob,
    setTimeout,
    clearTimeout,
    Date,
    Math,
    JSON,
    Intl,
    Promise
  };
  sandbox.window = sandbox;
  sandbox.globalThis = sandbox;
  sandbox.self = sandbox;
  if (options.seed) {
    for (const [k, v] of Object.entries(options.seed)) {
      ls.map.set(k, typeof v === 'string' ? v : JSON.stringify(v));
    }
  }
  const context = vm.createContext(sandbox);
  const root = options.root || ROOT;
  for (const rel of options.modules || DATA_MODULES) {
    const code = fs.readFileSync(path.join(root, rel), 'utf8');
    vm.runInContext(code, context, { filename: rel });
  }
  return { window: sandbox, App: sandbox.FocusModeApp, ls, logs, evaluate: code => vm.runInContext(code, context) };
}

function readFixture(name) {
  return fs.readFileSync(path.join(__dirname, '..', 'fixtures', name), 'utf8');
}

/* Minimal sequential test runner with readable output. */
function createRunner(title) {
  const queue = [];
  const results = [];
  function test(name, fn) {
    queue.push({ name, fn });
  }
  async function run() {
    for (const { name, fn } of queue) {
      try {
        await fn();
        results.push({ name, ok: true });
      } catch (error) {
        results.push({ name, ok: false, error: error && error.stack ? error.stack.split('\n').slice(0, 3).join('\n') : String(error) });
      }
    }
    const failed = results.filter(r => !r.ok);
    console.log(`\n# ${title}: ${results.length - failed.length}/${results.length} passed`);
    for (const r of results) {
      console.log(`${r.ok ? '  ok  ' : '  FAIL'} ${r.name}${r.ok ? '' : '\n        ' + r.error.replace(/\n/g, '\n        ')}`);
    }
    return failed.length;
  }
  return { test, run };
}

function assert(condition, message) {
  if (!condition) throw new Error('Assertion failed: ' + message);
}

function assertEqual(actual, expected, message) {
  if (actual !== expected) {
    throw new Error(`Assertion failed: ${message} (expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)})`);
  }
}

module.exports = { ROOT, DATA_MODULES, createContext, createLocalStorage, readFixture, createRunner, assert, assertEqual };
