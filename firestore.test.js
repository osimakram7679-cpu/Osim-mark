const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_osim";
const OWNER_EMAIL = "osimakram90644@gmail.com";
const CUSTOMER_UID = "customer_alice";
const OTHER_UID = "customer_bob";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function ownerDb() {
  return testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
}

function spoofedAdminDb() {
  return testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
}

function sampleProduct(now = new Date(Date.now() - 1000)) {
  return {
    name: "Mustard Oil 1L",
    description: "Pure cold-pressed kachi ghani mustard oil.",
    category: "Groceries & Staples",
    unit: "1 L",
    price: 165.0,
    mrp: 190.0,
    stockCount: 25,
    available: true,
    imageKey: "groceries",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot read or write products, orders, or store settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").where("available", "==", true).get());
  await assertFails(unauthDb.collection("store_settings").doc("main").get());
  await assertFails(unauthDb.collection("orders").where("userId", "==", CUSTOMER_UID).get());
  await assertFails(unauthDb.collection("products").doc("p1").set(sampleProduct()));
});

test("Product management: owner can create/update products and prices; customer and spoofed email cannot", async () => {
  const adminDb = ownerDb();
  const spoofDb = spoofedAdminDb();
  const custDb = testEnv.authenticatedContext(CUSTOMER_UID).firestore();

  await assertSucceeds(adminDb.collection("products").doc("prod_oil").set(sampleProduct()));

  // Owner updates product price and name
  await assertSucceeds(
    adminDb.collection("products").doc("prod_oil").update({
      price: 158.0,
      name: "Kachi Ghani Mustard Oil",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Customer can list available products, cannot list unfiltered or mutate
  await assertSucceeds(custDb.collection("products").where("available", "==", true).get());
  await assertFails(custDb.collection("products").get());
  await assertFails(custDb.collection("products").doc("prod_oil").update({ price: 1.0, updatedAt: new Date() }));

  // Spoofed unverified email rejected
  await assertFails(spoofDb.collection("products").doc("prod_spoof").set(sampleProduct()));
});

test("Orders: customer can place own order and cancel while PLACED; cross-user and status forgery rejected", async () => {
  const custDb = testEnv.authenticatedContext(CUSTOMER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const adminDb = ownerDb();
  const now = new Date(Date.now() - 1000);

  const validOrder = {
    userId: CUSTOMER_UID,
    customerName: "Amina Khatun",
    customerPhone: "7679100108",
    deliveryAddress: "Debiganj Bazar, Goalpokher, Uttar Dinajpur",
    items: [{ productId: "prod_oil", name: "Mustard Oil 1L", unit: "1 L", price: 165.0, quantity: 2 }],
    subtotal: 330.0,
    deliveryFee: 20.0,
    totalAmount: 350.0,
    paymentMethod: "UPI",
    paymentStatus: "PAID",
    paymentReference: "UPI-REF-1001",
    status: "PLACED",
    ownerNote: "",
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(custDb.collection("orders").doc("ord_1").set(validOrder));
  await assertSucceeds(custDb.collection("orders").where("userId", "==", CUSTOMER_UID).get());

  // Other user cannot read or list Alice's order
  await assertFails(otherDb.collection("orders").doc("ord_1").get());
  await assertFails(otherDb.collection("orders").get());

  // Customer cannot self-approve to DELIVERED
  await assertFails(
    custDb.collection("orders").doc("ord_1").update({
      status: "DELIVERED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin can update status and ownerNote
  await assertSucceeds(
    adminDb.collection("orders").doc("ord_1").update({
      status: "PACKED",
      ownerNote: "Packed and ready for dispatch",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Customer cannot cancel once PACKED
  await assertFails(
    custDb.collection("orders").doc("ord_1").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("User notifications: owner can notify customer; customer can read and mark seen", async () => {
  const adminDb = ownerDb();
  const custDb = testEnv.authenticatedContext(CUSTOMER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    adminDb.collection("users").doc(CUSTOMER_UID).collection("notifications").doc("n_1").set({
      orderId: "ord_1",
      title: "Order Packed",
      message: "Your order #ord_1 is packed!",
      status: "PACKED",
      seen: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(custDb.collection("users").doc(CUSTOMER_UID).collection("notifications").get());
  await assertSucceeds(
    custDb.collection("users").doc(CUSTOMER_UID).collection("notifications").doc("n_1").update({
      seen: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(otherDb.collection("users").doc(CUSTOMER_UID).collection("notifications").get());
});
