'use strict';

// Focus Mode+ v21 — trusted, recursive deletion of one account's Firestore data.
// Runs only with the Admin SDK on a trusted server (Cloud Functions). The uid always comes from
// the deleted Firebase Authentication user, never from a client request body.

const UID = /^[A-Za-z0-9_-]{1,128}$/;

/**
 * Deletes /users/{uid} and every subcollection below it (missions with their steps, sessions,
 * settings, syncMeta, tombstones and any collection a later version adds).
 * @param {import('firebase-admin/firestore').Firestore} db Admin Firestore instance
 * @param {string} uid Firebase UID of the deleted account
 */
async function deleteUserData(db, uid) {
  if (typeof uid !== 'string' || !UID.test(uid)) throw new Error('invalid_uid');
  await db.recursiveDelete(db.collection('users').doc(uid));
  return { deleted: true };
}

module.exports = { deleteUserData };
