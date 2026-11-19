'use strict';
/*
 * AI boundary tests: model output normalisation, safe IDs, caps, image signature/dimension
 * checks and error mapping in src/services/ai-service.js (loaded into a vm context).
 * Run: node tests/security/ai-boundary.test.js
 */
const { createContext, createRunner, assert, assertEqual } = require('./lib/harness');

const runner = createRunner('AI boundary (ai-service.js)');
const test = runner.test;

function loadAI() {
  return createContext({
    modules: [
      'src/core/architecture-shell.js',
      'src/services/storage-service.js',
      'src/data/schema-versions.js',
      'src/services/ai-service.js'
    ]
  });
}

function plan(sessions, extra = {}) {
  return { goal: { title: 'Ziel', subject: 'Mathe', type: 'exam', deadlineText: '' }, sessions, ...extra };
}

function step(id, extra = {}) {
  return { id, title: 'Schritt', task: 'Aufgabe', durationMinutes: 25, ...extra };
}

test('AI step IDs that could break out of inline handlers are replaced; duplicates become unique', () => {
  const ctx = loadAI();
  const isValid = ctx.App.data.schema.isValidEntityId;
  const out = ctx.window.normalizeAIPlan(plan([
    step("x');window.__xss=1;('"),
    step('ok-id'),
    step('ok-id'),
    step(undefined),
    step(42),
    step('"><img src=x onerror=alert(1)>')
  ]), null);
  const ids = out.sessions.map(s => s.id);
  assertEqual(new Set(ids).size, ids.length, 'unique ids');
  for (const id of ids) {
    assert(isValid(id), 'safe id ' + id);
    assert(!/['"<>()\s;]/.test(id), 'no breakout characters in ' + id);
  }
  assertEqual(ids[1], 'ok-id', 'valid id kept');
});

test('AI strings and lists are bounded; control characters removed', () => {
  const ctx = loadAI();
  const long = 'a'.repeat(10000);
  const out = ctx.window.normalizeAIPlan(plan([
    step('s1', { title: long, task: long + '\u0000\u0007', reason: long, requiredTools: new Array(50).fill('Stift'), recommendedApps: [{ name: 'Notes' }, 'Anki'] })
  ], {
    goal: { title: long, subject: long, type: 'exam', deadlineText: long },
    sourceAnalysis: { detectedTasks: long, facts: new Array(100).fill('Fakt'), estimates: [{ label: 'x', value: long }], missingInformation: ['?'] },
    estimate: { totalMinutes: 1e12, difficulty: long, explanation: long }
  }), null);
  const s = out.sessions[0];
  assert(out.goal.title.length <= 200, 'goal title capped');
  assert(out.goal.subject.length <= 120, 'subject capped');
  assert(s.title.length <= 200 && s.task.length <= 2000 && s.reason.length <= 2000, 'step text capped');
  assert(!/[\u0000\u0007]/.test(s.task), 'control chars removed');
  assert(s.requiredTools.length <= 12, 'tools bounded');
  assert(out.sourceAnalysis.facts.length <= 20, 'facts bounded');
  assert(out.sourceAnalysis.estimates[0].value.length <= 500, 'object list values capped');
  assert(out.estimate.totalMinutes <= 100000, 'totalMinutes bounded');
  assert(out.estimate.difficulty.length <= 40, 'difficulty capped');
});

test('existing plan validation still rejects incomplete plans', () => {
  const ctx = loadAI();
  let code = null;
  try { ctx.window.normalizeAIPlan(plan([]), null); } catch (e) { code = e.code; }
  assertEqual(code, 'INVALID_AI_RESPONSE', 'no sessions');
  code = null;
  try { ctx.window.normalizeAIPlan(plan([step('a', { durationMinutes: 500 })]), null); } catch (e) { code = e.code; }
  assertEqual(code, 'INVALID_AI_RESPONSE', 'duration out of range');
});

function pngHeader(w, h) {
  const b = new Uint8Array(33);
  b.set([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52]);
  new DataView(b.buffer).setUint32(16, w);
  new DataView(b.buffer).setUint32(20, h);
  return b;
}

function jpegWithExifThenSof(w, h) {
  const exif = [0xFF, 0xE1, 0x00, 0x10, 0x45, 0x78, 0x69, 0x66, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8];
  const sof = [0xFF, 0xC0, 0x00, 0x11, 0x08, (h >> 8) & 255, h & 255, (w >> 8) & 255, w & 255, 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1];
  return new Uint8Array([0xFF, 0xD8, ...exif, ...sof, 0xFF, 0xD9]);
}

function webpVP8X(w, h) {
  const b = new Uint8Array(30);
  b.set([0x52, 0x49, 0x46, 0x46, 22, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x58, 10, 0, 0, 0, 0, 0, 0, 0]);
  const W = w - 1, H = h - 1;
  b.set([W & 255, (W >> 8) & 255, (W >> 16) & 255, H & 255, (H >> 8) & 255, (H >> 16) & 255], 24);
  return b;
}

test('image signatures and header dimensions are read without decoding', () => {
  const ctx = loadAI();
  const sniff = ctx.window.sniffImageHeader;
  assertEqual(JSON.stringify(sniff(pngHeader(640, 480))), '{"mime":"image/png","width":640,"height":480}', 'png');
  assertEqual(JSON.stringify(sniff(jpegWithExifThenSof(4032, 3024))), '{"mime":"image/jpeg","width":4032,"height":3024}', 'jpeg after EXIF');
  assertEqual(JSON.stringify(sniff(webpVP8X(1200, 900))), '{"mime":"image/webp","width":1200,"height":900}', 'webp');
  assertEqual(sniff(new TextEncoder().encode('<svg onload=alert(1)></svg>')), null, 'svg/html rejected');
  assertEqual(sniff(new Uint8Array([1, 2, 3])), null, 'short input');
});

test('MIME/extension spoofing and decompression-bomb dimensions are rejected before decode', async () => {
  const ctx = loadAI();
  const inspect = ctx.window.inspectMissionImageFile;
  const spoofed = await inspect(new Blob([pngHeader(100, 100)]), 'image/jpeg');
  assert(!spoofed.ok, 'png bytes declared as jpeg rejected');
  const bomb = await inspect(new Blob([pngHeader(30000, 30000)]), 'image/png');
  assert(!bomb.ok, '30000x30000 rejected');
  const tooManyPixels = await inspect(new Blob([pngHeader(8000, 8000)]), 'image/png');
  assert(!tooManyPixels.ok, '64 MP rejected');
  const fine = await inspect(new Blob([jpegWithExifThenSof(4032, 3024)]), 'image/jpeg');
  assert(fine.ok, 'normal phone photo accepted');
});

test('error mapping: request ID only when safe, internal exception text never shown, server text capped', () => {
  const ctx = loadAI();
  const parse = ctx.window.parseMissionErrorResponse;
  const ok = parse(429, { ok: false, error: { code: 'AI_RATE_LIMITED', message: 'Bitte warten', requestId: 'req_abc123' } }, null);
  assertEqual(ok.code, 'AI_RATE_LIMITED', 'code');
  assertEqual(ok.requestId, 'req_abc123', 'request id kept');
  const bad = parse(500, { ok: false, error: { code: 'INTERNAL_ERROR', requestId: '<img src=x>' } }, null);
  assertEqual(bad.requestId, null, 'unsafe request id dropped');
  const internal = parse(0, null, new Error('Unexpected token < in JSON at position 0'));
  assert(!/Unexpected token/.test(internal.message), 'parser message not leaked');
  const long = parse(400, { ok: false, error: { code: 'INVALID_REQUEST', message: 'x'.repeat(1000) + '\u0000' } }, null);
  assert(long.message.length <= 300 && !long.message.includes('\u0000'), 'server message capped and cleaned');
});

module.exports = runner;
if (require.main === module) {
  runner.run().then(failed => { process.exitCode = failed ? 1 : 0; });
}
