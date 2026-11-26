'use strict';

// Focus Mode+ v21 — Cloud Functions (trusted server boundary). NOT deployed by the repository.
// Deploy only after the steps in android/docs/account-deletion-v21.md.
//
// The Android app deletes the account's documents itself (owner-only Rules) before it deletes
// the Firebase account. This trigger is the guarantee for anything left behind: documents written
// by another device in the meantime, or a deletion interrupted between the two steps.

const functions = require('firebase-functions/v1');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { deleteUserData } = require('./delete-user-data');

initializeApp();

// [TO BE CONFIRMED] region: keep it in the same European location as the Firestore database.
exports.deleteUserDataOnAccountDeletion = functions
  .region('europe-west3')
  .auth.user()
  .onDelete(async (user) => {
    await deleteUserData(getFirestore(), user.uid);
  });
