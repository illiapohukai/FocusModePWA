'use strict';

// Focus Mode+ v22 — adversarial Firestore Security Rules tests (Firebase Emulator Suite, demo
// project only; nothing here can reach a production project). Run: `npm run test:emulator`.
// Generated cases are bounded and deterministic (no randomness, a few hundred writes in total).

const { test, describe, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const {
  doc, collection, collectionGroup, query, where, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp,
} = require('firebase/firestore');

const PROJECT_ID = 'demo-focus-mode';
if (!PROJECT_ID.startsWith('demo-')) throw new Error('Rules tests run only against a demo project');
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Run through the Firebase Emulator Suite: npm run test:emulator');

const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'client-payloads.json'), 'utf8'));
const ALICE = fixture.uid;
const BOB = 'bobUid000002';
const MALLORY = 'malloryUid03';

function materialize(value) {
  if (Array.isArray(value)) return value.map(materialize);
  if (value && typeof value === 'object') {
    if (value.__serverTimestamp === true) return serverTimestamp();
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, materialize(v)]));
  }
  return value;
}
const clone = (v) => JSON.parse(JSON.stringify(v));
const owned = (payload, uid) => ({ ...clone(payload), ownerUid: uid });
const withField = (payload, changes) => ({ ...clone(payload), ...changes });
const without = (payload, key) => { const c = clone(payload); delete c[key]; return c; };

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8') },
  });
});
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env.cleanup(); });

const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();
const seed = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));

const MISSION_ID = fixture.missionCreate.id;
const missionRef = (db, uid, id = MISSION_ID) => doc(db, 'users', uid, 'missions', id);
const stepRef = (db, uid, stepId, missionId = MISSION_ID) => doc(db, 'users', uid, 'missions', missionId, 'steps', stepId);
const sessionRef = (db, uid, id = fixture.session.id) => doc(db, 'users', uid, 'sessions', id);
const settingsRef = (db, uid) => doc(db, 'users', uid, 'settings', 'profile');
const tombRef = (db, uid, id = fixture.tombstone.id) => doc(db, 'users', uid, 'tombstones', id);
const migrationRef = (db, uid, id = fixture.migration.migrationId) => doc(db, 'users', uid, 'syncMeta', `migration_${id}`);

function missionBatch(db, uid, mission, steps) {
  const b = writeBatch(db);
  b.set(missionRef(db, uid, mission.id), materialize(owned(mission, uid)));
  for (const s of steps) b.set(stepRef(db, uid, s.id, mission.id), materialize(owned(s, uid)));
  return b.commit();
}

/** Seeds a complete, valid account for `uid` (written as that user, through the Rules). */
async function seedAccount(uid) {
  const db = as(uid);
  await assertSucceeds(setDoc(doc(db, 'users', uid), materialize(owned(fixture.profile, uid))));
  await assertSucceeds(missionBatch(db, uid, fixture.missionCreate, fixture.steps));
  await assertSucceeds(setDoc(sessionRef(db, uid), materialize(owned(fixture.session, uid))));
  await assertSucceeds(setDoc(settingsRef(db, uid), materialize(owned(fixture.settings, uid))));
  await assertSucceeds(setDoc(migrationRef(db, uid), materialize(owned(fixture.migration, uid))));
}

const allDocRefs = (db, uid) => [
  doc(db, 'users', uid), missionRef(db, uid), stepRef(db, uid, fixture.steps[0].id), sessionRef(db, uid), settingsRef(db, uid), migrationRef(db, uid),
];

// ----------------------------------------------------------------------------- authentication

describe('unauthenticated and cross-account access', () => {
  test('unauthenticated clients can neither read, list, write nor delete anything', async () => {
    await seedAccount(ALICE);
    const db = anonymous();
    for (const ref of allDocRefs(db, ALICE)) {
      await assertFails(getDoc(ref));
      await assertFails(deleteDoc(ref));
    }
    for (const c of ['missions', 'sessions', 'settings', 'syncMeta', 'tombstones']) await assertFails(getDocs(collection(db, 'users', ALICE, c)));
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(setDoc(doc(db, 'users', ALICE), materialize(fixture.profile)));
    await assertFails(missionBatch(db, ALICE, fixture.missionCreate, fixture.steps));
    await assertFails(setDoc(settingsRef(db, ALICE), materialize(fixture.settings)));
  });

  test('user A cannot read, list, write or delete any document of user B', async () => {
    await seedAccount(BOB);
    const db = as(ALICE);
    for (const ref of allDocRefs(db, BOB)) {
      await assertFails(getDoc(ref));
      await assertFails(deleteDoc(ref));
    }
    for (const c of ['missions', 'sessions', 'settings', 'syncMeta', 'tombstones']) await assertFails(getDocs(collection(db, 'users', BOB, c)));
    await assertFails(getDocs(collection(db, 'users', BOB, 'missions', MISSION_ID, 'steps')));
    // Writing into B's tree, with A's or B's uid in the payload.
    for (const uid of [ALICE, BOB]) {
      await assertFails(setDoc(missionRef(db, BOB, 'mission_new'), materialize(owned(withField(fixture.missionCreate, { id: 'mission_new' }), uid))));
      await assertFails(setDoc(stepRef(db, BOB, 'step_new'), materialize(owned(withField(fixture.steps[1], { id: 'step_new' }), uid))));
      await assertFails(setDoc(settingsRef(db, BOB), materialize(owned(fixture.settings, uid))));
      await assertFails(setDoc(tombRef(db, BOB), materialize(owned(fixture.tombstone, uid))));
    }
    // B's data is unchanged.
    await seed(async (admin) => {
      const s = await getDoc(sessionRef(admin, BOB));
      assert.equal(s.data().ownerUid, BOB);
    });
  });

  test('collection-group queries across accounts are denied', async () => {
    await seedAccount(BOB);
    const db = as(ALICE);
    await assertFails(getDocs(collectionGroup(db, 'missions')));
    await assertFails(getDocs(collectionGroup(db, 'sessions')));
    await assertFails(getDocs(query(collectionGroup(db, 'sessions'), where('ownerUid', '==', ALICE))));
  });

  test('a document created under another uid with a self-consistent payload is still denied', async () => {
    const db = as(MALLORY);
    await assertFails(setDoc(doc(db, 'users', ALICE), materialize(owned(fixture.profile, ALICE))));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(owned(fixture.session, ALICE))));
    await assertFails(setDoc(migrationRef(db, ALICE), materialize(owned(fixture.migration, ALICE))));
  });
});

// ----------------------------------------------------------------------------- identity fields

describe('owner uid, ids and immutable fields', () => {
  test('owner uid and record ids cannot be changed on any document type', async () => {
    await seedAccount(ALICE);
    const db = as(ALICE);
    await assertFails(setDoc(doc(db, 'users', ALICE), materialize(owned(fixture.profile, BOB))));
    await assertFails(setDoc(missionRef(db, ALICE), materialize(owned(fixture.missionUpdate, BOB))));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { id: 'session_other' }))));
    await assertFails(setDoc(settingsRef(db, ALICE), materialize(owned(fixture.settings, BOB))));
    await assertFails(setDoc(migrationRef(db, ALICE), materialize(withField(fixture.migrationComplete, { startedAt: fixture.migration.startedAt + 1 }))));
    await assertFails(setDoc(missionRef(db, ALICE), materialize(withField(fixture.missionUpdate, { createdAt: fixture.missionUpdate.createdAt - 1 }))));
    await assertFails(setDoc(doc(db, 'users', ALICE), materialize(withField(fixture.profile, { createdAt: fixture.profile.createdAt + 1 }))));
  });

  test('a document id that differs from the payload id is denied', async () => {
    const db = as(ALICE);
    await assertFails(setDoc(sessionRef(db, ALICE, 'session_elsewhere'), materialize(fixture.session)));
    await assertFails(setDoc(missionRef(db, ALICE, 'mission_elsewhere'), materialize(fixture.missionCreate)));
    await assertFails(setDoc(tombRef(db, ALICE, 'mission_elsewhere'), materialize(fixture.tombstone)));
  });
});

// ----------------------------------------------------------------------------- reserved fields

describe('server-authoritative fields and paths', () => {
  const docs = () => [
    ['profile', (db, extra) => setDoc(doc(db, 'users', ALICE), materialize(withField(fixture.profile, extra)))],
    ['mission', (db, extra) => missionBatch(db, ALICE, withField(fixture.missionCreate, extra), fixture.steps)],
    ['step', (db, extra) => missionBatch(db, ALICE, fixture.missionCreate, [withField(fixture.steps[0], extra), fixture.steps[1]])],
    ['session', (db, extra) => setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, extra)))],
    ['settings', (db, extra) => setDoc(settingsRef(db, ALICE), materialize(withField(fixture.settings, extra)))],
    ['tombstone', (db, extra) => setDoc(tombRef(db, ALICE), materialize(withField(fixture.tombstone, extra)))],
    ['migration', (db, extra) => setDoc(migrationRef(db, ALICE), materialize(withField(fixture.migration, extra)))],
  ];
  const values = [1, 999999, true, 'gold', { tier: 'admin' }];

  test('every reserved field is denied on every document type, whatever its value', async () => {
    const db = as(ALICE);
    for (const [name, write] of docs()) {
      for (const field of fixture.reservedFields) {
        for (const value of values) await assertFails(write(db, { [field]: value }), `${name}.${field}=${JSON.stringify(value)}`);
      }
    }
  });

  test('League points, verified minutes, subscription, entitlement, quota, admin, moderation and trust paths are denied', async () => {
    const db = as(ALICE);
    const paths = [
      ['users', ALICE, 'league', 'season_1'], ['users', ALICE, 'verified', 'summary'], ['users', ALICE, 'verifiedSessions', 's1'],
      ['users', ALICE, 'subscription', 'current'], ['users', ALICE, 'entitlements', 'pro'], ['users', ALICE, 'aiQuota', 'month'],
      ['users', ALICE, 'admin', 'role'], ['users', ALICE, 'moderation', 'status'], ['users', ALICE, 'trust', 'score'],
      ['users', ALICE, 'missions', MISSION_ID, 'verified', 'x'], ['leagues', 'season_1'], ['admins', ALICE], ['entitlements', ALICE],
    ];
    for (const p of paths) {
      await assertFails(setDoc(doc(db, ...p), { ownerUid: ALICE, schemaVersion: 1, leaguePoints: 1 }), p.join('/'));
      await assertFails(getDoc(doc(db, ...p)), p.join('/'));
    }
  });
});

// ----------------------------------------------------------------------------- validation

describe('schema, size and value bounds', () => {
  test('future, downgraded and non-integer schema versions are denied everywhere', async () => {
    const db = as(ALICE);
    for (const v of [0, 2, 99, '1', 1.5, null]) {
      await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { schemaVersion: v }))), `session ${v}`);
      await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { schemaVersion: v }), fixture.steps), `mission ${v}`);
      await assertFails(setDoc(settingsRef(db, ALICE), materialize(withField(fixture.settings, { schemaVersion: v }))), `settings ${v}`);
    }
  });

  test('oversized strings and arrays are denied; the maximum is accepted', async () => {
    const db = as(ALICE);
    const L = fixture.limits;
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { task: 'x'.repeat(L.TEXT_MAX + 1) }))));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { reviewNote: 'x'.repeat(L.NOTE_MAX + 1) }))));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { zoneId: 'Z'.repeat(65) }))));
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { summary: 'x'.repeat(L.TEXT_MAX + 1) }), fixture.steps));
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { stepIds: Array.from({ length: 101 }, (_, i) => `step_${i}`) }), fixture.steps));
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { progress: Array.from({ length: 501 }, () => ({})) }), fixture.steps));
    await assertFails(missionBatch(db, ALICE, fixture.missionCreate, [withField(fixture.steps[0], { requiredTools: Array.from({ length: 21 }, (_, i) => `t${i}`) }), fixture.steps[1]]));
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { task: 'x'.repeat(L.TEXT_MAX), reviewNote: 'y'.repeat(L.NOTE_MAX) }))));
  });

  test('negative, excessive, fractional and inconsistent durations are denied', async () => {
    const db = as(ALICE);
    const bad = [
      { actualMinutes: -1 }, { plannedMinutes: -25 }, { elapsedMs: -1 }, { plannedMinutes: 1441 }, { actualMinutes: 1441 },
      { elapsedMs: 172800001 }, { plannedMinutes: 25.5 }, { elapsedMs: 1.5 }, { rating: 6 }, { rating: 0 }, { actualMinutes: '25' },
    ];
    for (const b of bad) await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, b))), JSON.stringify(b));
    for (const b of [{ durationMinutes: 0 }, { durationMinutes: 1441 }, { durationMinutes: -5 }, { position: -1 }, { position: 1000 }]) {
      await assertFails(missionBatch(db, ALICE, fixture.missionCreate, [withField(fixture.steps[0], b), fixture.steps[1]]), JSON.stringify(b));
    }
  });

  test('invalid timestamps are denied: strings, before 2000, after 2100, fractions, reversed', async () => {
    const db = as(ALICE);
    for (const t of ['2026-11-20T10:00:00Z', 946684799999, 4102444800001, 1790000000000.5, -1, true]) {
      await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { startedAt: t }))), `startedAt=${t}`);
      await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { updatedAt: t }), fixture.steps), `updatedAt=${t}`);
    }
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { finishedAt: fixture.session.startedAt - 1 }))));
    for (const d of ['2026-13-01T00', '10.12.2026', '20261210', '']) {
      await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { deadlineDate: d }), fixture.steps), `deadlineDate=${d}`);
    }
  });

  test('nested objects in scalar fields are denied', async () => {
    const db = as(ALICE);
    for (const field of ['title', 'summary', 'status', 'syncVersion', 'createdAt', 'deviceMutationId']) {
      await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { [field]: { leaguePoints: 1 } }), fixture.steps), field);
    }
    for (const field of ['task', 'outcome', 'actualMinutes', 'countsAsFocus', 'missionId', 'reviewNote']) {
      await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { [field]: { admin: true } }))), field);
    }
  });

  test('DOCUMENTED LIMITATION: list items are not validated by the Rules (the Android decoder rejects them; they are never server-trusted)', async () => {
    // Rules cannot iterate lists, so a client can store odd items inside its own bounded lists.
    // The client codec refuses such a Mission on download (RemoteCodec.decodeProgress requires the exact
    // progress-entry field set), and no server code reads these lists. Tracked in residual-risks-v22.md.
    const db = as(ALICE);
    await assertSucceeds(missionBatch(db, ALICE, withField(fixture.missionCreate, { progress: [{ leaguePoints: 999999 }] }), fixture.steps));
  });

  test('a Session awaiting review, an unknown end reason or an unknown status never synchronizes', async () => {
    const db = as(ALICE);
    for (const b of [{ endReason: 'awaiting_user_review' }, { endReason: 'admin_override' }, { status: 'running' }, { status: 'verified' }, { outcome: 'perfect' }, { protectionMode: 'root' }, { origin: 'server' }]) {
      await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, b))), JSON.stringify(b));
    }
  });
});

// ----------------------------------------------------------------------------- v22 hardening

describe('v22: Session type and Mission link agree (V22-F02)', () => {
  test('a Quick Session carrying a Mission link is denied; a Mission Session without one is denied', async () => {
    const db = as(ALICE);
    const mission = fixture.session;
    assert.equal(mission.type, 'mission');
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(mission, { type: 'quick' }))));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(mission, { missionId: null, missionStepId: null }))));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(mission, { missionStepId: null }))));
    await assertSucceeds(setDoc(sessionRef(db, ALICE, 'session_quick_ok'), materialize(withField(mission, { id: 'session_quick_ok', type: 'quick', missionId: null, missionStepId: null }))));
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(mission)));
  });
});

describe('v22: steps follow the Mission version (V22-F07)', () => {
  test('a step written alone with another version or mutation id is denied', async () => {
    await assertSucceeds(missionBatch(as(ALICE), ALICE, fixture.missionCreate, fixture.steps));
    const db = as(ALICE);
    const open = fixture.steps.find((s) => s.status !== 'completed');
    await assertFails(setDoc(stepRef(db, ALICE, open.id), materialize(withField(open, { title: 'Heimlich geändert', syncVersion: 2 }))));
    await assertFails(setDoc(stepRef(db, ALICE, open.id), materialize(withField(open, { title: 'Heimlich geändert', deviceMutationId: 'aaaaaaaaaaaaaaaa' }))));
    await assertFails(setDoc(stepRef(db, ALICE, 'step_injected'), materialize(withField(open, { id: 'step_injected', position: 7, syncVersion: 9 }))));
  });

  test('a stale device that lost the Mission race cannot overwrite steps afterwards', async () => {
    await assertSucceeds(missionBatch(as(ALICE), ALICE, fixture.missionCreate, fixture.steps));
    const db = as(ALICE);
    // Device 1 wins: Mission v2 with its steps.
    const v2Steps = fixture.steps.map((s) => withField(s, { syncVersion: fixture.missionUpdate.syncVersion, deviceMutationId: fixture.missionUpdate.deviceMutationId }));
    await assertSucceeds(missionBatch(db, ALICE, fixture.missionUpdate, v2Steps));
    // Device 2 still holds v1 and writes only a step at its old version.
    const stale = fixture.steps.find((s) => s.status !== 'completed');
    await assertFails(setDoc(stepRef(db, ALICE, stale.id), materialize(withField(stale, { title: 'Veraltet' }))));
    // Its whole v2 batch is refused by the Mission's version check as well.
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionUpdate, { deviceMutationId: 'bbbbbbbbbbbbbbbb' }), fixture.steps.map((s) => withField(s, { syncVersion: 2, deviceMutationId: 'bbbbbbbbbbbbbbbb' }))));
  });

  test('the client batch shape (Mission and every step, same version and id) is accepted', async () => {
    await assertSucceeds(missionBatch(as(ALICE), ALICE, fixture.missionCreate, fixture.steps));
    const v2Steps = fixture.steps.map((s) => withField(s, { syncVersion: 2, deviceMutationId: fixture.missionUpdate.deviceMutationId }));
    await assertSucceeds(missionBatch(as(ALICE), ALICE, fixture.missionUpdate, v2Steps));
  });
});

// ----------------------------------------------------------------------------- history and replay

describe('versions, replays, tombstones and History', () => {
  test('replaying a Mission mutation or decreasing its version is denied', async () => {
    await assertSucceeds(missionBatch(as(ALICE), ALICE, fixture.missionCreate, fixture.steps));
    const db = as(ALICE);
    await assertSucceeds(setDoc(missionRef(db, ALICE), materialize(fixture.missionUpdate)));
    await assertFails(setDoc(missionRef(db, ALICE), materialize(fixture.missionUpdate)), 'replay of v2');
    await assertFails(setDoc(missionRef(db, ALICE), materialize(withField(fixture.missionUpdate, { syncVersion: 1 }))), 'downgrade');
    await assertFails(setDoc(missionRef(db, ALICE), materialize(withField(fixture.missionUpdate, { syncVersion: 0 }))), 'zero');
    await assertFails(setDoc(missionRef(db, ALICE), materialize(withField(fixture.missionUpdate, { syncVersion: 1000000001 }))), 'overflow');
  });

  test('a deleted Mission cannot be resurrected at any version while its tombstone exists', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(tombRef(db, ALICE), materialize(fixture.tombstone)));
    const gone = fixture.tombstone.entityId;
    const steps = fixture.steps.map((s) => withField(s, { missionId: gone }));
    for (const v of [1, 2, 7]) {
      const m = withField(fixture.missionCreate, { id: gone, syncVersion: v });
      await assertFails(missionBatch(db, ALICE, m, steps.map((s) => withField(s, { syncVersion: v }))), `v${v}`);
    }
  });

  test('malformed tombstones are denied', async () => {
    const db = as(ALICE);
    const t = fixture.tombstone;
    const bad = [
      { entityType: 'session' }, { entityType: 'league' }, { entityId: '..' }, { entityId: 'a/b' }, { id: 'mission_other' },
      { syncVersion: 0 }, { deletedAt: -1 }, { deletedAt: 'gestern' }, { deviceMutationId: 'x' }, { extra: true },
    ];
    for (const b of bad) await assertFails(setDoc(tombRef(db, ALICE, b.id || t.id), materialize(withField(t, b))), JSON.stringify(b));
    await assertFails(setDoc(tombRef(db, ALICE), materialize(without(t, 'deletedAt'))));
  });

  test('a finalized Session cannot be rewritten and a duplicate id cannot overwrite it', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(fixture.session)));
    for (const b of [{ actualMinutes: 1440 }, { countsAsFocus: false }, { finishedAt: fixture.session.finishedAt + 60000 }, { type: 'quick', missionId: null, missionStepId: null }, { localDate: '2026-01-01' }]) {
      await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, b))), JSON.stringify(b));
      await assertFails(updateDoc(sessionRef(db, ALICE), { ...b, serverUpdatedAt: serverTimestamp() }), `update ${JSON.stringify(b)}`);
    }
  });

  test('DOCUMENTED LIMITATION: the owner may delete and re-create their own unverified History', async () => {
    // Deleting is required for account deletion. Client-written History is ordinary, unverified
    // data; any future League value is computed server-side and never read from these documents.
    const db = as(ALICE);
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(fixture.session)));
    await assertSucceeds(deleteDoc(sessionRef(db, ALICE)));
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { actualMinutes: 1440, plannedMinutes: 1440 }))));
  });

  test('migration documents are bound to their id and owner; another account cannot attach local data', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(migrationRef(db, ALICE), materialize(fixture.migration)));
    await assertFails(setDoc(migrationRef(db, ALICE, 'migration_other_id'), materialize(fixture.migration)));
    await assertFails(setDoc(migrationRef(db, ALICE), materialize(withField(fixture.migration, { migrationId: 'zzzzzzzzzzzzzzzz' }))));
    await assertFails(setDoc(migrationRef(as(BOB), ALICE), materialize(fixture.migration)));
    await assertFails(setDoc(migrationRef(db, ALICE), materialize(withField(fixture.migrationComplete, { completedAt: null }))));
    await assertFails(setDoc(migrationRef(db, ALICE), materialize(withField(fixture.migration, { missionCount: -1 }))));
  });
});

// ----------------------------------------------------------------------------- generated cases

describe('bounded generated cases (deterministic)', () => {
  /** A value of the wrong type for each field, derived from the valid fixture value. */
  function wrongValues(valid) {
    const out = [{ nested: { leaguePoints: 1 } }];
    if (Array.isArray(valid)) out.push('not-a-list');
    else if (typeof valid === 'string') out.push(42);
    else if (typeof valid === 'number') out.push('1', true);
    else if (typeof valid === 'boolean') out.push('true', 0);
    else if (valid === null) out.push([]);
    return out;
  }

  const types = [
    ['session', fixture.session, (db, p) => setDoc(sessionRef(db, ALICE), materialize(p))],
    ['settings', fixture.settings, (db, p) => setDoc(settingsRef(db, ALICE), materialize(p))],
    ['tombstone', fixture.tombstone, (db, p) => setDoc(tombRef(db, ALICE), materialize(p))],
    ['mission', fixture.missionCreate, (db, p) => rawBatch(db, p, fixture.steps)],
    ['step', fixture.steps[0], (db, p) => rawBatch(db, fixture.missionCreate, [p, fixture.steps[1]])],
  ];

  /** Fixed document paths and the payloads exactly as generated (no owner or id rewriting). */
  function rawBatch(db, mission, steps) {
    const b = writeBatch(db);
    b.set(missionRef(db, ALICE), materialize(mission));
    steps.forEach((s, i) => b.set(stepRef(db, ALICE, fixture.steps[i].id), materialize(s)));
    return b.commit();
  }

  for (const [name, valid, write] of types) {
    test(`${name}: every wrong-typed field and every missing field is denied`, async () => {
      const db = as(ALICE);
      let cases = 0;
      for (const key of Object.keys(valid)) {
        if (key === 'serverUpdatedAt') continue;
        for (const bad of wrongValues(valid[key])) {
          await assertFails(write(db, withField(valid, { [key]: bad })), `${name}.${key}=${JSON.stringify(bad)}`);
          cases++;
        }
        await assertFails(write(db, without(valid, key)), `${name} without ${key}`);
        cases++;
      }
      await assertSucceeds(write(db, clone(valid)));
      assert.ok(cases > 10 && cases < 200, `bounded case count ${cases}`);
    });
  }
});
