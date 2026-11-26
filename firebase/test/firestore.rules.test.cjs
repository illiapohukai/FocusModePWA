'use strict';

// Focus Mode+ v21 — Firestore Security Rules tests (Firebase Emulator Suite + the official
// @firebase/rules-unit-testing library). Run: `npm run test:emulator` in firebase/.
// Only the demo project is ever used; nothing here can reach production.

const { test, describe, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp } = require('firebase/firestore');

const PROJECT_ID = 'demo-focus-mode';
if (!PROJECT_ID.startsWith('demo-')) throw new Error('Rules tests run only against a demo project');
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Run through the Firebase Emulator Suite: npm run test:emulator');

const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'client-payloads.json'), 'utf8'));
const ALICE = fixture.uid;
const BOB = 'bobUid000002';

/** Replaces the codec's server-timestamp marker with the SDK sentinel, deep-copying the payload. */
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

const missionRef = (db, uid, id = fixture.missionCreate.id) => doc(db, 'users', uid, 'missions', id);
const stepRef = (db, uid, stepId, missionId = fixture.missionCreate.id) => doc(db, 'users', uid, 'missions', missionId, 'steps', stepId);
const sessionRef = (db, uid, id = fixture.session.id) => doc(db, 'users', uid, 'sessions', id);
const migrationRef = (db, uid, id = fixture.migration.migrationId) => doc(db, 'users', uid, 'syncMeta', `migration_${id}`);

/** The Android client writes a Mission and all its steps in one atomic batch. */
function missionBatch(db, uid, mission, steps) {
  const b = writeBatch(db);
  b.set(missionRef(db, uid, mission.id), materialize(owned(mission, uid)));
  for (const s of steps) b.set(stepRef(db, uid, s.id, mission.id), materialize(owned(s, uid)));
  return b.commit();
}

async function aliceHasMission() {
  await assertSucceeds(missionBatch(as(ALICE), ALICE, fixture.missionCreate, fixture.steps));
}

describe('client payloads written by the Android codec', () => {
  test('owner valid writes are allowed for every document type', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(doc(db, 'users', ALICE), materialize(fixture.profile)));
    await assertSucceeds(missionBatch(db, ALICE, fixture.missionCreate, fixture.steps));
    await assertSucceeds(setDoc(missionRef(db, ALICE), materialize(fixture.missionUpdate)));
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(fixture.session)));
    await assertSucceeds(setDoc(doc(db, 'users', ALICE, 'settings', 'profile'), materialize(fixture.settings)));
    await assertSucceeds(setDoc(doc(db, 'users', ALICE, 'tombstones', fixture.tombstone.id), materialize(fixture.tombstone)));
    await assertSucceeds(setDoc(migrationRef(db, ALICE), materialize(fixture.migration)));
    await assertSucceeds(setDoc(migrationRef(db, ALICE), materialize(fixture.migrationComplete)));
  });

  test('owner reads are allowed', async () => {
    await aliceHasMission();
    const db = as(ALICE);
    await assertSucceeds(getDoc(missionRef(db, ALICE)));
    await assertSucceeds(getDocs(collection(db, 'users', ALICE, 'missions')));
    await assertSucceeds(getDocs(collection(db, 'users', ALICE, 'missions', fixture.missionCreate.id, 'steps')));
  });

  test('a Mission with the maximum of 100 steps fits one batch', async () => {
    const stepIds = Array.from({ length: 100 }, (_, i) => `step_${String(i).padStart(3, '0')}`);
    const mission = withField(fixture.missionCreate, { id: 'mission_big', stepIds, currentStepId: stepIds[0], progress: [] });
    const steps = stepIds.map((id, i) => withField(fixture.steps[1], { id, missionId: 'mission_big', position: i, status: i === 0 ? 'active' : 'pending' }));
    await assertSucceeds(missionBatch(as(ALICE), ALICE, mission, steps));
  });
});

describe('authentication and ownership', () => {
  test('unauthenticated reads and writes are denied', async () => {
    await aliceHasMission();
    const db = anonymous();
    await assertFails(getDoc(missionRef(db, ALICE)));
    await assertFails(getDoc(doc(db, 'users', ALICE)));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(fixture.session)));
    await assertFails(setDoc(doc(db, 'users', ALICE), materialize(fixture.profile)));
  });

  test('user A cannot read user B', async () => {
    await assertSucceeds(missionBatch(as(BOB), BOB, fixture.missionCreate, fixture.steps));
    const db = as(ALICE);
    await assertFails(getDoc(missionRef(db, BOB)));
    await assertFails(getDocs(collection(db, 'users', BOB, 'missions')));
    await assertFails(getDocs(collection(db, 'users', BOB, 'sessions')));
  });

  test('user A cannot write user B, neither as A nor claiming B', async () => {
    const db = as(ALICE);
    await assertFails(setDoc(sessionRef(db, BOB), materialize(owned(fixture.session, BOB))));
    await assertFails(setDoc(sessionRef(db, BOB), materialize(fixture.session)));
    await assertFails(missionBatch(db, BOB, fixture.missionCreate, fixture.steps));
    await assertFails(setDoc(doc(db, 'users', BOB), materialize(owned(fixture.profile, BOB))));
  });

  test('the owner uid cannot be changed or set to another account', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(doc(db, 'users', ALICE), materialize(fixture.profile)));
    await assertFails(setDoc(doc(db, 'users', ALICE), materialize(owned(fixture.profile, BOB))));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(owned(fixture.session, BOB))));
    await aliceHasMission();
    await assertFails(setDoc(missionRef(db, ALICE), materialize(owned(fixture.missionUpdate, BOB))));
  });

  test('immutable ids and creation times cannot change', async () => {
    await aliceHasMission();
    const db = as(ALICE);
    await assertFails(setDoc(missionRef(db, ALICE), materialize(withField(fixture.missionUpdate, { id: 'mission_other' }))));
    await assertFails(setDoc(missionRef(db, ALICE), materialize(withField(fixture.missionUpdate, { createdAt: fixture.missionUpdate.createdAt + 1 }))));
    await assertFails(setDoc(stepRef(db, ALICE, fixture.steps[1].id), materialize(withField(fixture.steps[1], { missionId: 'mission_other' }))));
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(fixture.session)));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { id: 'session_other' }))));
  });

  test('cross-account migration is denied', async () => {
    const db = as(ALICE);
    await assertFails(setDoc(migrationRef(db, BOB), materialize(owned(fixture.migration, BOB))));
    await assertFails(setDoc(migrationRef(db, BOB), materialize(fixture.migration)));
    await assertFails(setDoc(migrationRef(db, ALICE), materialize(owned(fixture.migration, BOB))));
    // The document id must belong to the migration it describes.
    await assertFails(setDoc(migrationRef(db, ALICE, 'migration9999'), materialize(fixture.migration)));
  });
});

describe('reserved server-authoritative fields', () => {
  for (const field of fixture.reservedFields) {
    test(`${field} cannot be written by a client`, async () => {
      const db = as(ALICE);
      await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { [field]: 1 }), fixture.steps));
      await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { [field]: 1 }))));
      await assertFails(setDoc(doc(db, 'users', ALICE), materialize(withField(fixture.profile, { [field]: 1 }))));
      await assertFails(setDoc(doc(db, 'users', ALICE, 'settings', 'profile'), materialize(withField(fixture.settings, { [field]: 'premium' }))));
    });
  }

  test('server-only paths are denied to mobile clients', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users', ALICE, 'verified', 'summary'), { verifiedFocusMinutes: 120 });
      await setDoc(doc(db, 'leagues', 'season_1'), { leagueSeason: 's1' });
    });
    const db = as(ALICE);
    for (const p of [['users', ALICE, 'verified', 'summary'], ['users', ALICE, 'ledger', 'entry_1'], ['users', ALICE, 'entitlements', 'pro'],
      ['leagues', 'season_1'], ['admin', 'roles'], ['serverOnly', 'x']]) {
      await assertFails(getDoc(doc(db, ...p)));
      await assertFails(setDoc(doc(db, ...p), { ownerUid: ALICE, schemaVersion: 1 }));
    }
  });
});

describe('field validation', () => {
  test('an oversized Mission title is denied; the maximum is accepted', async () => {
    const db = as(ALICE);
    const max = fixture.limits.TITLE_MAX;
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { title: 'x'.repeat(max + 1) }), fixture.steps));
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { title: '   ' }), fixture.steps));
    await assertSucceeds(missionBatch(db, ALICE, withField(fixture.missionCreate, { title: 'x'.repeat(max) }), fixture.steps));
  });

  test('invalid Session durations are denied', async () => {
    const db = as(ALICE);
    for (const bad of [{ actualMinutes: -1 }, { plannedMinutes: 0 }, { plannedMinutes: fixture.limits.MINUTES_MAX + 1 }, { elapsedMs: 172800001 },
      { actualMinutes: 2.5 }, { finishedAt: fixture.session.startedAt - 1 }]) {
      await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, bad))), JSON.stringify(bad));
    }
  });

  test('unknown or missing fields are denied', async () => {
    const db = as(ALICE);
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { colour: 'red' }), fixture.steps));
    const { summary, ...missing } = clone(fixture.missionCreate);
    assert.ok(summary !== undefined);
    await assertFails(missionBatch(db, ALICE, missing, fixture.steps));
    await assertFails(setDoc(sessionRef(db, ALICE), materialize(withField(fixture.session, { verification: 'server_verified' }))));
  });

  test('an invalid schema is denied', async () => {
    const db = as(ALICE);
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { schemaVersion: 2 }), fixture.steps));
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { status: 'verified' }), fixture.steps));
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { deadlineDate: '10.12.2026' }), fixture.steps));
    await assertFails(missionBatch(db, ALICE, withField(fixture.missionCreate, { createdAt: '2026-11-20' }), fixture.steps));
    await assertFails(setDoc(doc(db, 'users', ALICE, 'settings', 'profile'), materialize(withField(fixture.settings, { defaultQuickFocusMinutes: 7 }))));
    await assertFails(setDoc(doc(db, 'users', ALICE, 'settings', 'other'), materialize(fixture.settings)));
  });

  test('unsafe document ids are denied', async () => {
    const db = as(ALICE);
    // User text is never an id: the Rules refuse ids outside the safe-id policy.
    await assertFails(setDoc(sessionRef(db, ALICE, 'Lernen am Abend'), materialize(withField(fixture.session, { id: 'Lernen am Abend' }))));
    await assertFails(setDoc(sessionRef(db, ALICE, 'a'), materialize(withField(fixture.session, { id: 'a' }))));
    // Reserved Firestore ids are refused by Firestore itself, before the Rules run.
    await assert.rejects(setDoc(sessionRef(db, ALICE, '__session__'), materialize(withField(fixture.session, { id: '__session__' }))));
  });

  test('client clocks cannot replace the server commit time', async () => {
    await assertFails(setDoc(sessionRef(as(ALICE), ALICE), withField(materialize(fixture.session), { serverUpdatedAt: Date.now() })));
  });

  test('a Session still awaiting its review never synchronizes', async () => {
    await assertFails(setDoc(sessionRef(as(ALICE), ALICE), materialize(withField(fixture.session, { endReason: 'awaiting_user_review' }))));
  });
});

describe('versions, history and deletion', () => {
  test('a stale or skipping Mission version is denied (optimistic concurrency)', async () => {
    await aliceHasMission();
    const db = as(ALICE);
    await assertFails(setDoc(missionRef(db, ALICE), materialize(withField(fixture.missionUpdate, { syncVersion: 1 }))));
    await assertFails(setDoc(missionRef(db, ALICE), materialize(withField(fixture.missionUpdate, { syncVersion: 3 }))));
    await assertSucceeds(setDoc(missionRef(db, ALICE), materialize(fixture.missionUpdate)));
    await assertFails(missionBatch(db, ALICE, fixture.missionCreate, fixture.steps)); // a second "create" at version 1
  });

  test('finalized Sessions are append-only except for the note', async () => {
    const db = as(ALICE);
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(fixture.session)));
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(fixture.session))); // idempotent retry
    await assertSucceeds(updateDoc(sessionRef(db, ALICE), { reviewNote: 'Neu formuliert', serverUpdatedAt: serverTimestamp() }));
    await assertFails(updateDoc(sessionRef(db, ALICE), { actualMinutes: 60, serverUpdatedAt: serverTimestamp() }));
    await assertFails(updateDoc(sessionRef(db, ALICE), { outcome: 'partial', serverUpdatedAt: serverTimestamp() }));
  });

  test('a completed step is never reopened or rewritten', async () => {
    await aliceHasMission();
    const db = as(ALICE);
    const done = fixture.steps.find((s) => s.status === 'completed');
    await assertFails(setDoc(stepRef(db, ALICE, done.id), materialize(withField(done, { status: 'pending', completedAt: null }))));
    await assertFails(setDoc(stepRef(db, ALICE, done.id), materialize(withField(done, { title: 'Umbenannt' }))));
    await assertSucceeds(setDoc(stepRef(db, ALICE, done.id), materialize(withField(done, { position: 0 }))));
  });

  test('a valid tombstone is accepted and blocks resurrection; invalid tombstones are denied', async () => {
    const db = as(ALICE);
    await assertFails(setDoc(doc(db, 'users', ALICE, 'tombstones', 'mission_x'), materialize(fixture.tombstone)));
    await assertFails(setDoc(doc(db, 'users', ALICE, 'tombstones', fixture.tombstone.id), materialize(withField(fixture.tombstone, { entityType: 'session' }))));
    await assertFails(setDoc(doc(db, 'users', ALICE, 'tombstones', fixture.tombstone.id), materialize(withField(fixture.tombstone, { entityId: '..' }))));
    await assertSucceeds(setDoc(doc(db, 'users', ALICE, 'tombstones', fixture.tombstone.id), materialize(fixture.tombstone)));
    const gone = withField(fixture.missionCreate, { id: fixture.tombstone.entityId });
    const steps = fixture.steps.map((s) => withField(s, { missionId: gone.id }));
    await assertFails(missionBatch(db, ALICE, gone, steps));
  });

  test('a step needs its Mission in the same batch or before', async () => {
    await assertFails(setDoc(stepRef(as(ALICE), ALICE, fixture.steps[1].id, 'mission_missing'), materialize(withField(fixture.steps[1], { missionId: 'mission_missing' }))));
  });

  test('the owner can delete every document; nobody else can', async () => {
    await aliceHasMission();
    const db = as(ALICE);
    await assertSucceeds(setDoc(sessionRef(db, ALICE), materialize(fixture.session)));
    await assertFails(deleteDoc(sessionRef(as(BOB), ALICE)));
    await assertFails(deleteDoc(missionRef(anonymous(), ALICE)));
    for (const s of fixture.steps) await assertSucceeds(deleteDoc(stepRef(db, ALICE, s.id)));
    await assertSucceeds(deleteDoc(missionRef(db, ALICE)));
    await assertSucceeds(deleteDoc(sessionRef(db, ALICE)));
    await assertSucceeds(deleteDoc(doc(db, 'users', ALICE)));
  });
});
