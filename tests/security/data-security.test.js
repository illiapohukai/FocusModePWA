'use strict';
/*
 * Import / export / validator / storage security tests (Node, no dependencies).
 * Run: node tests/security/data-security.test.js
 * Optional forward-compatibility check against an older checkout:
 *   FM_BASELINE_ROOT=/path/to/v17.16-checkout node tests/security/data-security.test.js
 */
const { createContext, readFixture, createRunner, assert, assertEqual } = require('./lib/harness');

const runner = createRunner('Data security (import/export/storage)');
const test = runner.test;

const SAFE_STEP = (mid, i, status) => ({
  id: `step_${i}_${mid.slice(-6)}`,
  missionId: mid,
  title: 'Schritt ' + (i + 1),
  task: 'Aufgabe ' + (i + 1),
  durationMinutes: 25,
  status,
  createdAt: 1790000000000,
  updatedAt: 1790000000000,
  completedAt: status === 'completed' ? 1790000100000 : null
});

function missionFixture(id, overrides = {}) {
  const steps = [SAFE_STEP(id, 0, 'completed'), SAFE_STEP(id, 1, 'active')];
  return {
    id,
    title: 'Mission ' + id.slice(-4),
    subject: 'Mathe',
    type: 'exam',
    deadlineText: '2026-12-01',
    status: 'active',
    createdAt: 1790000000000,
    updatedAt: 1790000000000,
    currentStepId: steps[1].id,
    estimate: { totalMinutes: 50, difficulty: 'medium', explanation: 'Schätzung' },
    sourceAnalysis: { detectedTasks: '', detectedPages: '', confidence: 0, missingInformation: [], facts: [], estimates: [] },
    steps,
    progress: { completedStepIds: [steps[0].id], history: [], initialStepCount: 2 },
    ...overrides
  };
}

function sessionFixture(i, overrides = {}) {
  return {
    id: 'session_test_' + i,
    type: 'quick',
    status: 'completed',
    title: 'Fokus ' + i,
    task: 'Konzentriert arbeiten',
    plannedMinutes: 25,
    actualMinutes: 25,
    startedAt: 1790000000000 + i * 3600000,
    finishedAt: 1790000000000 + i * 3600000 + 1500000,
    missionId: null,
    missionStepId: null,
    result: { outcome: null, feedback: null },
    ...overrides
  };
}

function bundle({ missions = [missionFixture('mission_aaaaaa')], sessions = [sessionFixture(1)], extra = {}, dataExtra = {} } = {}) {
  return {
    exportVersion: 1,
    applicationVersion: 'v16 Data Stability',
    exportedAt: '2026-11-01T10:00:00.000Z',
    schemaVersions: { bundle: 1, missions: 1, missionSteps: 1, sessions: 1, sessionHistory: 1, preferences: 1, recovery: 1 },
    data: {
      missions: { schemaVersion: 1, stepSchemaVersion: 1, missions, selectedMissionId: missions[0]?.id ?? null, primaryMissionId: missions.find(m => m.status === 'active')?.id ?? null, updatedAt: 1790000000000 },
      sessionHistory: { schemaVersion: 1, sessions },
      activeSession: null,
      lastSession: null,
      preferences: { schemaVersion: 1, language: 'de' },
      ...dataExtra
    },
    ...extra
  };
}

async function prepare(ctx, value, name = 'test.json') {
  const text = typeof value === 'string' ? value : JSON.stringify(value);
  return ctx.App.data.importer.prepareImportFromTextAsync(text, name);
}

function seededContext(options = {}) {
  const ctx = createContext(options);
  const result = ctx.App.data.importer.importFromJSONTextDirect(readFixture('export-v17.16-full.json'), 'seed.json');
  assert(result.ok, 'seed import must succeed: ' + JSON.stringify(result.errors || result.code));
  return ctx;
}

function snapshot(ctx) {
  return JSON.stringify(Array.from(ctx.ls.map.entries()).filter(([k]) => !k.includes('backup')).sort());
}

/* ---------------- IMPORT ---------------- */

test('valid current (v17.17) export imports with integrity verified', async () => {
  const src = seededContext();
  const ex = await src.App.data.exporter.exportUserData({ download: false });
  assert(ex.ok, 'export ok');
  const dst = createContext();
  const p = await prepare(dst, ex.json);
  assert(p.ok, 'prepare ok ' + p.code);
  assertEqual(p.preview.integrity, 'verified', 'integrity verified');
  assertEqual(p.preview.format, 'focusmode-backup', 'format detected');
  const r = dst.App.data.importer.confirmPreparedImport();
  assert(r.ok, 'confirm ok ' + r.code);
  assertEqual(r.missionCount, 4, 'missions');
  assertEqual(r.sessionCount, 12, 'sessions');
});

test('older v17.16 export (fixture from pre-change code) imports and keeps IDs and progress', async () => {
  const fixture = JSON.parse(readFixture('export-v17.16-full.json'));
  const ctx = createContext();
  const p = await prepare(ctx, fixture);
  assert(p.ok, 'prepare ok ' + p.code);
  assertEqual(p.preview.integrity, 'absent', 'older backup has no digest');
  const r = ctx.App.data.importer.confirmPreparedImport();
  assert(r.ok, 'confirm ok');
  const stored = JSON.parse(ctx.ls.map.get('focus-mode-missions-v11-5'));
  const before = fixture.data.missions.missions;
  assertEqual(stored.missions.length, before.length, 'mission count');
  for (const m of before) {
    const after = stored.missions.find(x => x.id === m.id);
    assert(after, 'mission id preserved ' + m.id);
    assertEqual(after.title, m.title, 'title preserved');
    assertEqual(after.status, m.status, 'status preserved');
    assertEqual(JSON.stringify(after.steps.map(s => [s.id, s.status])), JSON.stringify(m.steps.map(s => [s.id, s.status])), 'steps preserved');
    assertEqual(JSON.stringify(after.progress.completedStepIds), JSON.stringify(m.progress.completedStepIds), 'progress preserved');
  }
  const history = JSON.parse(ctx.ls.map.get('focusmode-session-history-v11-1'));
  assertEqual(JSON.stringify(history.sessions.map(s => s.id).sort()), JSON.stringify(fixture.data.sessionHistory.sessions.map(s => s.id).sort()), 'session ids preserved');
  assertEqual(stored.selectedMissionId, fixture.data.missions.selectedMissionId, 'selected mission preserved');
});

test('legacy root-array export imports', async () => {
  const ctx = createContext();
  const p = await prepare(ctx, readFixture('export-legacy-root-arrays.json'));
  assert(p.ok, 'prepare ok ' + p.code);
  assert(ctx.App.data.importer.confirmPreparedImport().ok, 'confirm ok');
});

test('unsupported future schema is rejected', async () => {
  const b = bundle();
  b.schemaVersions.bundle = 99;
  const p = await prepare(createContext(), b);
  assertEqual(p.code, 'unsupported_future_schema', 'code');
});

test('unknown format marker and unknown scope are rejected', async () => {
  assertEqual((await prepare(createContext(), bundle({ extra: { format: 'other-app-backup' } }))).code, 'unsupported_format', 'format');
  assertEqual((await prepare(createContext(), bundle({ extra: { scope: 'everything' } }))).code, 'unsupported_format', 'scope');
});

test('malformed JSON, empty file, non-object root are rejected', async () => {
  assertEqual((await prepare(createContext(), '{"exportVersion":1,')).code, 'invalid_json', 'malformed');
  assertEqual((await prepare(createContext(), '   ')).code, 'empty_file', 'empty');
  assertEqual((await prepare(createContext(), '[1,2,3]')).code, 'invalid_bundle_format', 'array root');
});

test('oversized file is rejected before reading', async () => {
  const ctx = createContext();
  let readCalled = false;
  const fakeFile = { name: 'big.json', size: 6 * 1024 * 1024, text: async () => { readCalled = true; return '{}'; } };
  const p = await ctx.App.data.importer.prepareImportFromFile(fakeFile);
  assertEqual(p.code, 'file_too_large', 'code');
  assert(!readCalled, 'file.text() must not be called');
  const big = '{"pad":"' + 'x'.repeat(5 * 1024 * 1024 + 10) + '"}';
  assertEqual((await prepare(ctx, big)).code, 'file_too_large', 'text path');
});

test('deep nesting is rejected without stack overflow', async () => {
  let text = '{"a":'.repeat(5000) + '1' + '}'.repeat(5000);
  const p = await prepare(createContext(), text);
  assertEqual(p.code, 'import_too_deep', 'code');
});

test('huge arrays and domain limits are rejected', async () => {
  const arr = bundle({ dataExtra: { lastAiPlan: { sessions: new Array(10001).fill(1) } } });
  assertEqual((await prepare(createContext(), arr)).code, 'import_array_too_long', 'generic array limit');
  const many = [];
  for (let i = 0; i < 251; i += 1) many.push(missionFixture('mission_m' + String(i).padStart(5, '0'), { status: 'completed', currentStepId: null, steps: [SAFE_STEP('mission_m' + i, 0, 'completed')] }));
  const p = await prepare(createContext(), bundle({ missions: many }));
  assertEqual(p.code, 'import_too_many_missions', 'mission limit');
  const longTitle = bundle({ missions: [missionFixture('mission_bbbbbb', { title: 'x'.repeat(301) })] });
  assertEqual((await prepare(createContext(), longTitle)).code, 'import_title_too_long', 'title length');
});

test('dangerous keys (__proto__, constructor, prototype) are rejected and nothing is polluted', async () => {
  const ctx = createContext();
  const proto = '{"exportVersion":1,"data":{"missions":{"missions":[]},"sessionHistory":{"sessions":[]},"preferences":{"__proto__":{"polluted":true}}}}';
  assertEqual((await prepare(ctx, proto)).code, 'import_dangerous_key', '__proto__');
  const ctor = bundle({ dataExtra: { preferences: { language: 'de', constructor: { prototype: { polluted: true } } } } });
  assertEqual((await prepare(ctx, ctor)).code, 'import_dangerous_key', 'constructor');
  const nested = bundle();
  nested.data.missions.missions[0].steps[0].prototype = 'x';
  assertEqual((await prepare(ctx, nested)).code, 'import_dangerous_key', 'nested prototype');
  assert(({}).polluted === undefined, 'Node Object.prototype not polluted');
  assert(ctx.evaluate('Object.prototype.polluted') === undefined, 'context Object.prototype not polluted');
});

test('reserved server-authoritative fields are rejected (no pre-populated verified data)', async () => {
  const b = bundle();
  b.data.sessionHistory.sessions[0].verifiedStreak = 999;
  assertEqual((await prepare(createContext(), b)).code, 'import_reserved_server_field', 'verifiedStreak');
  const c = bundle({ dataExtra: { preferences: { language: 'de', subscriptionTier: 'pro' } } });
  assertEqual((await prepare(createContext(), c)).code, 'import_reserved_server_field', 'subscriptionTier');
});

test('invalid IDs are rejected', async () => {
  const b = bundle({ missions: [missionFixture('mission_cccccc', { id: "bad');alert(1);('" })] });
  b.data.missions.selectedMissionId = null;
  b.data.missions.primaryMissionId = null;
  const p = await prepare(createContext(), b);
  assert(!p.ok, 'rejected');
  assert(['mission_id_invalid', 'step_mission_id_mismatch'].includes(p.code), 'code ' + p.code);
});

test('invalid dates and timestamps are rejected', async () => {
  const b = bundle();
  b.data.missions.missions[0].steps[0].scheduledDate = '2026-02-30';
  assertEqual((await prepare(createContext(), b)).code, 'import_date_invalid', 'impossible date');
  const c = bundle();
  c.data.missions.missions[0].createdAt = 'yesterday-ish';
  assertEqual((await prepare(createContext(), c)).code, 'import_timestamp_invalid', 'bad timestamp');
  const d = bundle({ sessions: [sessionFixture(1, { finishedAt: 9e15 })] });
  assertEqual((await prepare(createContext(), d)).code, 'import_timestamp_invalid', 'far-future timestamp');
});

test('invalid durations are rejected', async () => {
  const b = bundle();
  b.data.missions.missions[0].steps[1].durationMinutes = 99999;
  assertEqual((await prepare(createContext(), b)).code, 'import_duration_invalid', 'step duration');
  const c = bundle({ sessions: [sessionFixture(1, { plannedMinutes: -5 })] });
  assertEqual((await prepare(createContext(), c)).code, 'import_duration_invalid', 'negative planned');
});

test('invalid completion / session state is rejected', async () => {
  const b = bundle({ sessions: [sessionFixture(1, { status: 'done' })] });
  assertEqual((await prepare(createContext(), b)).code, 'session_status_invalid', 'unknown status');
  const c = bundle({ sessions: [sessionFixture(1, { status: 'running' })] });
  assertEqual((await prepare(createContext(), c)).code, 'session_terminal_status_required', 'non-terminal in history');
  const d = bundle();
  d.data.missions.missions[0].steps[0].status = 'finished';
  assertEqual((await prepare(createContext(), d)).code, 'step_status_invalid', 'step status');
});

test('duplicate IDs are rejected', async () => {
  const b = bundle({ sessions: [sessionFixture(1), sessionFixture(1)] });
  assertEqual((await prepare(createContext(), b)).code, 'duplicate_session_id', 'sessions');
  const m = missionFixture('mission_dddddd');
  const c = bundle({ missions: [m, JSON.parse(JSON.stringify(m))] });
  assertEqual((await prepare(createContext(), c)).code, 'duplicate_mission_id', 'missions');
});

test('unknown Mission/step fields are dropped; HTML and script text stay plain text', async () => {
  const ctx = createContext();
  const xssTitle = '<img src=x onerror="window.__xss=1">';
  const m = missionFixture('mission_eeeeee', { title: xssTitle, onclick: 'alert(1)', evilConfig: { handler: 'x' } });
  m.steps[1].task = '<script>window.__xss=2</script> javascript:alert(1)';
  m.steps[1].onmouseover = 'alert(1)';
  const p = await prepare(ctx, bundle({ missions: [m] }));
  assert(p.ok, 'prepare ok ' + p.code);
  assert(ctx.App.data.importer.confirmPreparedImport().ok, 'confirm ok');
  const stored = JSON.parse(ctx.ls.map.get('focus-mode-missions-v11-5')).missions[0];
  assertEqual(stored.title, xssTitle, 'title stored verbatim as text');
  assert(!('onclick' in stored) && !('evilConfig' in stored), 'unknown mission fields dropped');
  assert(!('onmouseover' in stored.steps[1]), 'unknown step fields dropped');
  assertEqual(stored.steps[1].task, '<script>window.__xss=2</script> javascript:alert(1)', 'task stored as text');
});

test('integrity: match verifies, mismatch and malformed digest are rejected, absent crypto is reported honestly', async () => {
  const src = seededContext();
  const ex = await src.App.data.exporter.exportUserData({ download: false });
  const tampered = JSON.parse(ex.json);
  tampered.data.missions.missions[0].title += ' (bearbeitet)';
  assertEqual((await prepare(createContext(), tampered)).code, 'integrity_mismatch', 'mismatch');
  const truncatedDigest = JSON.parse(ex.json);
  truncatedDigest.integrity.digest = truncatedDigest.integrity.digest.slice(0, 20);
  assertEqual((await prepare(createContext(), truncatedDigest)).code, 'integrity_invalid', 'malformed digest');
  const reformatted = JSON.stringify(JSON.parse(ex.json));
  assertEqual((await prepare(createContext(), reformatted)).preview.integrity, 'verified', 'whitespace-independent (canonical)');
  const noCrypto = createContext({ noCrypto: true });
  const pNo = await prepare(noCrypto, ex.json);
  assertEqual(pNo.preview.integrity, 'not_checked', 'no crypto: digest not checked, not claimed verified');
  const srcNo = createContext({ noCrypto: true });
  srcNo.App.data.importer.importFromJSONTextDirect(readFixture('export-v17.16-full.json'));
  const exNo = await srcNo.App.data.exporter.exportUserData({ download: false });
  assertEqual(exNo.integrityStatus, 'unavailable', 'export without crypto marks unavailable');
  assertEqual(JSON.parse(exNo.json).integrity.digest, null, 'no fake digest');
});

test('partial write failure rolls back to the exact previous state', async () => {
  const ctx = seededContext();
  const before = snapshot(ctx);
  const next = bundle({ missions: [missionFixture('mission_ffffff')], sessions: [sessionFixture(7)] });
  const p = await prepare(ctx, next);
  assert(p.ok, 'prepare ok');
  ctx.ls.state.failKeys.add('focusmode-session-history-v11-1'); // missions write succeeds, history write fails
  const r = ctx.App.data.importer.confirmPreparedImport();
  assert(!r.ok, 'import must fail');
  assertEqual(r.code, 'storage_write_failed', 'code');
  assert(r.rolledBack === true, 'rolled back');
  ctx.ls.state.failKeys.clear();
  assertEqual(snapshot(ctx), before, 'storage identical to pre-import state');
});

test('quota exhaustion during import rolls back; quota is classified', async () => {
  const ctx = seededContext();
  const before = snapshot(ctx);
  const huge = missionFixture('mission_gggggg');
  huge.steps[1].task = 'y'.repeat(19000);
  const p = await prepare(ctx, bundle({ missions: [huge] }));
  assert(p.ok, 'prepare ok');
  // Let the pre-import backup succeed, then cap storage so the bigger dataset cannot be written.
  ctx.App.data.migrationRunner.createBackupChecked('pre_import', 'focus-mode-backup-import-v16', { updateLatest: true });
  const used = Array.from(ctx.ls.map.entries()).reduce((n, [k, v]) => n + (k.length + v.length) * 2, 0);
  ctx.ls.state.quotaBytes = used + 100;
  const r = ctx.App.data.importer.confirmPreparedImport();
  assert(!r.ok, 'import must fail');
  assert(['storage_write_failed', 'backup_failed'].includes(r.code), 'code ' + r.code);
  ctx.ls.state.quotaBytes = Infinity;
  assertEqual(snapshot(ctx), before, 'storage unchanged');
  assertEqual(ctx.App.services.storage.getLastWriteError().code, 'storage_quota_exceeded', 'quota classified');
});

test('pre-import backup is required: failure aborts before any change', async () => {
  const ctx = seededContext();
  const before = snapshot(ctx);
  const p = await prepare(ctx, bundle());
  assert(p.ok, 'prepare ok');
  ctx.ls.state.failKeys.add('focus-mode-backup-import-v16');
  const r = ctx.App.data.importer.confirmPreparedImport();
  assertEqual(r.code, 'backup_failed', 'code');
  assertEqual(snapshot(ctx), before, 'nothing changed');
});

test('successful import leaves a restorable pre-import backup', async () => {
  const ctx = seededContext();
  const p = await prepare(ctx, bundle());
  assert(p.ok && ctx.App.data.importer.confirmPreparedImport().ok, 'import ok');
  const backup = JSON.parse(ctx.ls.map.get('focus-mode-backup-import-v16'));
  assertEqual(backup.reason, 'pre_import', 'backup reason');
  assertEqual(backup.integrity.missionCount, 4, 'backup holds previous missions');
  const restored = ctx.App.data.reset.restoreLastBackup('focus-mode-backup-import-v16');
  assert(restored.ok, 'restore ok ' + restored.code);
  assertEqual(restored.missionCount, 4, 'restored mission count');
});

test('"Nur Missionen" backup keeps the current Session History on import', async () => {
  const src = seededContext();
  const ex = await src.App.data.exporter.exportUserData({ download: false, scope: 'missions' });
  const json = JSON.parse(ex.json);
  assertEqual(json.scope, 'missions', 'scope');
  assertEqual(json.data.sessionHistory.sessions.length, 0, 'no history in file');
  assertEqual(json.data.activeSession, null, 'no active session in file');
  const dst = createContext();
  dst.App.data.importer.importFromJSONTextDirect(JSON.stringify(bundle({ missions: [missionFixture('mission_hhhhhh')], sessions: [sessionFixture(3), sessionFixture(4)] })));
  const p = await prepare(dst, ex.json);
  assert(p.ok, 'prepare ok ' + p.code);
  assertEqual(p.preview.policy, 'replace_missions_keep_history', 'policy');
  assertEqual(p.preview.integrity, 'verified', 'verified');
  assert(dst.App.data.importer.confirmPreparedImport().ok, 'confirm ok');
  const history = JSON.parse(dst.ls.map.get('focusmode-session-history-v11-1'));
  assertEqual(history.sessions.length, 2, 'current history kept');
  assertEqual(JSON.parse(dst.ls.map.get('focus-mode-missions-v11-5')).missions.length, 4, 'missions replaced');
});

test('a newer file selection supersedes an older pending preview', async () => {
  const ctx = createContext();
  const first = prepare(ctx, bundle({ missions: [missionFixture('mission_iiiiii')] }));
  const second = prepare(ctx, bundle({ missions: [missionFixture('mission_jjjjjj')] }));
  const [a, b] = await Promise.all([first, second]);
  assertEqual(a.code, 'import_superseded', 'first superseded');
  assert(b.ok, 'second ok');
  assertEqual(ctx.App.data.importer.pendingImport.bundle.data.missions.missions[0].id, 'mission_jjjjjj', 'pending is the latest file');
});

/* ---------------- EXPORT ---------------- */

test('export envelope: format, versions, trust marker, digest, safe filename, no secrets', async () => {
  const ctx = seededContext();
  ctx.ls.map.set('focus-mode-language', 'de');
  const ex = await ctx.App.data.exporter.exportUserData({ download: false });
  const json = JSON.parse(ex.json);
  assertEqual(json.format, 'focusmode-backup', 'format');
  assertEqual(json.exportVersion, 1, 'bundle schema stays 1 (backward compatible)');
  assertEqual(json.appVersion, 'v17.17', 'app version');
  assertEqual(json.scope, 'full', 'scope');
  assertEqual(json.trust, 'local_backup_unverified', 'trust marker');
  assertEqual(json.integrity.algorithm, 'SHA-256', 'algorithm');
  assert(/^[0-9a-f]{64}$/.test(json.integrity.digest), 'digest hex');
  assert(!('checksum' in json.integrity), 'legacy unverified checksum removed');
  assert(/^focus-mode-backup-\d{4}-\d{2}-\d{2}\.json$/.test(ex.filename), 'filename ' + ex.filename);
  assert(!('rawSnapshot' in json) && !('migrationMetadata' in json.data), 'internal data excluded');
  assert(!/GEMINI|api[_-]?key|secret|token|password/i.test(ex.json), 'no secret-like content');
  const exM = await ctx.App.data.exporter.exportUserData({ download: false, scope: 'missions' });
  assert(/^focus-mode-backup-missionen-\d{4}-\d{2}-\d{2}\.json$/.test(exM.filename), 'missions filename');
});

test('export filename never contains user content', () => {
  const ctx = createContext();
  const name = ctx.App.data.exporter.formatExportFilename(new Date(Date.UTC(2026, 0, 2, 12)), 'missions');
  assert(/^[a-z0-9.-]+$/.test(name), 'only safe characters');
});

test('canonicalization is deterministic and key-order independent', async () => {
  const ctx = createContext();
  const integrity = ctx.App.data.validator.integrity;
  const a = { b: [3, { y: 1, x: 'ä"\n' }], a: null, c: { e: 1.5, d: undefined } };
  const b = { c: { e: 1.5 }, a: null, b: [3, { x: 'ä"\n', y: 1 }] };
  assertEqual(integrity.canonicalJSON(a), integrity.canonicalJSON(b), 'same canonical form');
  assertEqual(integrity.canonicalJSON(a), '{"a":null,"b":[3,{"x":"ä\\"\\n","y":1}],"c":{"e":1.5}}', 'exact form');
  const src = seededContext();
  const e1 = JSON.parse((await src.App.data.exporter.exportUserData({ download: false })).json);
  const e2 = JSON.parse((await src.App.data.exporter.exportUserData({ download: false })).json);
  assertEqual(e1.integrity.digest, e2.integrity.digest, 'same data -> same digest (exportedAt not covered)');
});

test('forward compatibility: a v17.17 export imports in v17.16 code (optional)', async () => {
  const baseline = process.env.FM_BASELINE_ROOT;
  if (!baseline) return; // skipped unless a baseline checkout is provided
  const src = seededContext();
  const ex = await src.App.data.exporter.exportUserData({ download: false });
  const old = createContext({ root: baseline });
  const r = old.App.data.importer.importFromJSONTextDirect(ex.json, 'new.json');
  assert(r.ok, 'v17.16 importer accepts v17.17 export: ' + (r.code || ''));
  assertEqual(r.missionCount, 4, 'missions');
});

/* ---------------- STORAGE ---------------- */

test('full deletion removes every app key (incl. backups) and nothing else', () => {
  const ctx = seededContext();
  ctx.ls.map.set('other-site-key', 'keep');
  ctx.ls.map.set('focus-mode-home-onboarding-v17-16', '1');
  assert(ctx.ls.map.has('focus-mode-backup-latest-v16') || ctx.ls.map.has('focus-mode-backup-import-v16'), 'a backup exists before');
  const r = ctx.App.data.reset.deleteAllLocalData();
  assert(r.ok, 'ok');
  const left = Array.from(ctx.ls.map.keys());
  assertEqual(JSON.stringify(left), JSON.stringify(['other-site-key']), 'only foreign key left');
});

test('corrupt stored JSON falls back safely and is quarantined', () => {
  const ctx = createContext({ seed: { 'focus-mode-language': 'de' } });
  ctx.ls.map.set('focus-test-corrupt', '{not json');
  const v = ctx.App.services.storage.getJSON('focus-test-corrupt', { ok: 1 }, { backupKey: 'focus-test-corrupt-q' });
  assertEqual(JSON.stringify(v), '{"ok":1}', 'fallback');
  assertEqual(ctx.ls.map.get('focus-test-corrupt-q'), '{not json', 'quarantined');
});

test('trust boundary: no local domain is server-authoritative', () => {
  const ctx = createContext();
  const schema = ctx.App.data.schema;
  for (const domain of ['missions', 'sessionHistory', 'localStreak', 'localAchievements', 'preferences']) {
    assertEqual(schema.isServerAuthoritative(domain), false, domain);
  }
  assert(schema.reservedServerFields.includes('leagueScore'), 'leagueScore reserved');
});

module.exports = runner;
if (require.main === module) {
  runner.run().then(failed => { process.exitCode = failed ? 1 : 0; });
}
