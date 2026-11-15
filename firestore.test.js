const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const DEFAULT_AISLES = [
  "VEG_FRUIT",
  "BAKERY",
  "DAIRY",
  "MEAT_FISH",
  "DRY_GOODS",
  "HOME_CHEM",
  "OTHER",
];

function pastDate() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("households")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(unauthDb.collection("householdInvites").doc("ABC234").get());
});

test("UserProfile: owner can create/update own profile; cross-user access and shadow fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alicja",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Shadow field injection fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(),
    })
  );

  // Cross-user read fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Household creation batch + invite join + subcollection CRUD + outsider isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const householdId = "house_1";
  const inviteCode = "K7M4P9";
  const ts = pastDate();

  // Create household + invite + default chore atomically in a batch
  const batch = aliceDb.batch();
  const houseRef = aliceDb.collection("households").doc(householdId);
  batch.set(houseRef, {
    name: "Mieszkanie",
    inviteCode,
    ownerId: ALICE_UID,
    memberUids: [ALICE_UID],
    members: {
      [ALICE_UID]: { displayName: "Alicja", weight: 1.0 },
    },
    aisleOrder: DEFAULT_AISLES,
    settlementCycle: 1,
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(aliceDb.collection("householdInvites").doc(inviteCode), {
    inviteCode,
    householdId,
    householdName: "Mieszkanie",
    ownerId: ALICE_UID,
    createdAt: ts,
  });
  batch.set(houseRef.collection("choreTiles").doc("chore_1"), {
    householdId,
    memberUids: [ALICE_UID],
    title: "Pies nakarmiony",
    iconKey: "pets",
    isPinnedToWidget: true,
    resetMode: "SCHEDULED_HOURS",
    resetHours: [8, 18],
    createdAt: ts,
    updatedAt: ts,
  });
  await assertSucceeds(batch.commit());

  // Invite list query is strictly blocked
  await assertFails(bobDb.collection("householdInvites").get());

  // Bob looks up exact invite code
  await assertSucceeds(bobDb.collection("householdInvites").doc(inviteCode).get());

  // Bob joins household
  await assertSucceeds(
    bobDb.collection("households").doc(householdId).update({
      memberUids: [ALICE_UID, BOB_UID],
      [`members.${BOB_UID}`]: { displayName: "Marek", weight: 1.0 },
      updatedAt: pastDate(),
    })
  );

  // Bob can now create a shopping item & expense
  await assertSucceeds(
    bobDb
      .collection("households")
      .doc(householdId)
      .collection("shoppingItems")
      .doc("item_1")
      .set({
        householdId,
        memberUids: [ALICE_UID, BOB_UID],
        name: "Mleko",
        quantityText: "2 l",
        category: "DAIRY",
        isPurchased: false,
        addedByUid: BOB_UID,
        addedByName: "Marek",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertSucceeds(
    bobDb
      .collection("households")
      .doc(householdId)
      .collection("expenses")
      .doc("exp_1")
      .set({
        householdId,
        memberUids: [ALICE_UID, BOB_UID],
        amountGrosze: 14800,
        description: "Biedronka",
        paidByUid: BOB_UID,
        paidByName: "Marek",
        cycleNumber: 1,
        category: "GROCERIES",
        createdByUid: BOB_UID,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Members can update household monthly budget & category limits
  await assertSucceeds(
    aliceDb.collection("households").doc(householdId).update({
      monthlyBudgetGrosze: 400000,
      categoryBudgetsGrosze: { GROCERIES: 180000, BILLS: 120000 },
      updatedAt: pastDate(),
    })
  );

  // Outsider cannot update household budget
  await assertFails(
    charlieDb.collection("households").doc(householdId).update({
      monthlyBudgetGrosze: 1000,
      updatedAt: pastDate(),
    })
  );

  // Invalid expense category fails
  await assertFails(
    bobDb
      .collection("households")
      .doc(householdId)
      .collection("expenses")
      .doc("bad_exp_cat")
      .set({
        householdId,
        memberUids: [ALICE_UID, BOB_UID],
        amountGrosze: 1000,
        description: "Test",
        paidByUid: BOB_UID,
        paidByName: "Marek",
        cycleNumber: 1,
        category: "UNKNOWN_CAT",
        createdByUid: BOB_UID,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Matching memberUids query succeeds for Bob
  await assertSucceeds(
    bobDb
      .collection("households")
      .doc(householdId)
      .collection("shoppingItems")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Unfiltered list query fails (rules are not filters)
  await assertFails(
    bobDb.collection("households").doc(householdId).collection("shoppingItems").get()
  );

  // Outsider Charlie cannot read or list household or subcollections
  await assertFails(charlieDb.collection("households").doc(householdId).get());
  await assertFails(
    charlieDb
      .collection("households")
      .doc(householdId)
      .collection("shoppingItems")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Value poisoning (invalid category or negative expense) fails
  await assertFails(
    bobDb
      .collection("households")
      .doc(householdId)
      .collection("shoppingItems")
      .doc("bad_item")
      .set({
        householdId,
        memberUids: [ALICE_UID, BOB_UID],
        name: "Test",
        quantityText: "",
        category: "INVALID_CAT",
        isPurchased: false,
        addedByUid: BOB_UID,
        addedByName: "Marek",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  await assertFails(
    bobDb
      .collection("households")
      .doc(householdId)
      .collection("expenses")
      .doc("bad_exp")
      .set({
        householdId,
        memberUids: [ALICE_UID, BOB_UID],
        amountGrosze: -500,
        description: "Negative",
        paidByUid: BOB_UID,
        paidByName: "Marek",
        cycleNumber: 1,
        createdByUid: BOB_UID,
        createdAt: ts,
        updatedAt: ts,
      })
  );
});
