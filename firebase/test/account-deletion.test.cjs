'use strict';

// Focus Mode+ v21 — the trusted recursive account-data deletion (functions/delete-user-data.js)
// against the Firestore emulator with the Admin SDK. Demo project only.

const { test, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { deleteUserData } = require('../functions/delete-user-data');

const PROJECT_ID = 'demo-focus-mode';
if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Run through the Firebase Emulator Suite: npm run test:emulator');

let app;
let db;
before(() => {
  app = initializeApp({ projectId: PROJECT_ID }, 'account-deletion-test');
  db = getFirestore(app);
});
after(async () => { await deleteApp(app); });

async function seedAccount(uid) {
  const user = db.collection('users').doc(uid);
  await user.set({ ownerUid: uid });
  await user.collection('missions').doc('mission_a').set({ ownerUid: uid });
  await user.collection('missions').doc('mission_a').collection('steps').doc('step_1').set({ ownerUid: uid });
  await user.collection('sessions').doc('session_1').set({ ownerUid: uid });
  await user.collection('settings').doc('profile').set({ ownerUid: uid });
  await user.collection('syncMeta').doc('migration_m1').set({ ownerUid: uid });
  await user.collection('tombstones').doc('mission_old').set({ ownerUid: uid });
  // A collection a future version might add: removed as well.
  await user.collection('future').doc('x').set({ ownerUid: uid });
}

async function documentsBelow(uid) {
  const out = [];
  async function walk(ref) {
    for (const c of await ref.listCollections()) {
      for (const d of (await c.get()).docs) { out.push(d.ref.path); await walk(d.ref); }
    }
  }
  const root = db.collection('users').doc(uid);
  if ((await root.get()).exists) out.push(root.path);
  await walk(root);
  return out;
}

beforeEach(async () => {
  for (const uid of ['aliceUid0001', 'bobUid000002']) await db.recursiveDelete(db.collection('users').doc(uid));
});

test('recursive deletion removes the whole account tree and nothing of other accounts', async () => {
  await seedAccount('aliceUid0001');
  await seedAccount('bobUid000002');
  assert.equal((await documentsBelow('aliceUid0001')).length, 8);
  await deleteUserData(db, 'aliceUid0001');
  assert.deepEqual(await documentsBelow('aliceUid0001'), []);
  assert.equal((await documentsBelow('bobUid000002')).length, 8);
});

test('deletion is idempotent', async () => {
  await seedAccount('aliceUid0001');
  await deleteUserData(db, 'aliceUid0001');
  await deleteUserData(db, 'aliceUid0001');
  assert.deepEqual(await documentsBelow('aliceUid0001'), []);
});

test('only a well-formed uid is accepted', async () => {
  for (const bad of ['', '../bobUid000002', 'users/x', null, 42, 'a'.repeat(129)]) {
    await assert.rejects(deleteUserData(db, bad), /invalid_uid/);
  }
});
