// Creates Auth accounts for the first 5 seeded users so you can log in as them.
// Usage: GOOGLE_APPLICATION_CREDENTIALS=key.json node scripts/seed-auth.js [--clean]
const admin = require("firebase-admin");
admin.initializeApp({ projectId: "drip-ae9a5" });

const PASSWORD = "DripTest123!";
const COUNT = 5;

(async () => {
  const clean = process.argv.includes("--clean");
  for (let i = 1; i <= COUNT; i++) {
    const uid = `seed_${String(i).padStart(2, "0")}`;
    const email = `seed${String(i).padStart(2, "0")}@drip.test`;
    if (clean) {
      await admin.auth().deleteUser(uid).catch(() => {});
      continue;
    }
    try {
      await admin.auth().createUser({ uid, email, password: PASSWORD, emailVerified: true });
    } catch (e) {
      if (e.code !== "auth/uid-already-exists" && e.code !== "auth/email-already-exists") throw e;
      await admin.auth().updateUser(uid, { email, password: PASSWORD });
    }
    console.log(`${email} / ${PASSWORD}`);
  }
})().catch((e) => { console.error(e); process.exit(1); });
