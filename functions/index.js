/**
 * This Cloud Function is triggered when a new group is created or when new members
 * are added to the 'pendingMembers' field of an existing group document.
 *
 * It performs the following actions:
 * 1. Reads the list of `pendingMembers` from the group document.
 * 2. For each user ID in the list, it updates the corresponding user document in the
 *    'users' collection, setting their 'groupId' to the ID of the group.
 * 3. After processing, it clears the `pendingMembers` field in the group document
 *    to prevent re-triggering.
 *
 * This server-side approach ensures that even if the client has restrictive
 * permissions, the backend can securely and reliably update user profiles to
 * assign them to the correct group.
 */
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();

exports.assignGroupToUsers = functions.firestore
    .document("groups/{groupId}")
    .onWrite(async (change, context) => {
      const {groupId} = context.params;
      const groupData = change.after.data();

      // Exit if the document was deleted or if there are no pending members
      if (!groupData || !groupData.pendingMembers) {
        console.log("No pending members to process.");
        return null;
      }

      const pendingMembers = groupData.pendingMembers;

      // Exit if the pendingMembers array is empty
      if (!Array.isArray(pendingMembers) || pendingMembers.length === 0) {
        console.log("pendingMembers array is empty or not an array.");
        return null;
      }

      console.log(`Processing ${pendingMembers.length} members for group ${groupId}.`);

      const batch = db.batch();

      // Update each user's document with the new groupId
      pendingMembers.forEach((userId) => {
        const userRef = db.collection("users").doc(userId);
        batch.update(userRef, {groupId: groupId});
      });

      // Clear the pendingMembers field to prevent the function from re-running
      const groupRef = db.collection("groups").doc(groupId);
      batch.update(groupRef, {pendingMembers: admin.firestore.FieldValue.delete()});

      try {
        await batch.commit();
        console.log(`Successfully assigned ${pendingMembers.length} members to group ${groupId} and cleared pending list.`);
        return {status: "success"};
      } catch (error) {
        console.error(`Error assigning members to group ${groupId}:`, error);
        // Optionally, you could add retry logic or further error handling here
        return {status: "error", error: error.message};
      }
    });

