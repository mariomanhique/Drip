// Seeds starter challenges. Idempotent: re-running overwrites the same ids.
// Usage: GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json node scripts/seed-challenges.js
const admin = require("firebase-admin");
admin.initializeApp({ projectId: "drip-ae9a5" });

const challenges = [
  { id: "porto-sneakers", title: "Sneakers Only", description: "Build the look around your best pair of sneakers.",
    rules: "One outfit per person. Sneakers must be visible.", category: "STREETWEAR", constraint: "OPEN" },
  { id: "porto-one-colour", title: "One Colour, One Outfit", description: "Pick a single colour and commit.",
    rules: "Tones of one colour only; neutrals allowed as accents.", category: "COLOUR", constraint: "CLOSET" },
  { id: "porto-business-casual", title: "Business Casual", description: "Smart without the stiffness.",
    rules: "Office-appropriate. No full suits.", category: "FORMAL", constraint: "BUDGET" },
  { id: "porto-thrift-flip", title: "Thrift Flip", description: "Show your best second-hand find.",
    rules: "Every piece must be second-hand.", category: "VINTAGE", constraint: "THRIFT" },
];

(async () => {
  const db = admin.firestore();
  const batch = db.batch();
  for (const { id, ...c } of challenges) {
    batch.set(db.doc(`challenges/${id}`), { ...c, sponsor: null, closed: false, createdAt: Date.now() }, { merge: true });
  }
  await batch.commit();
  console.log(`Seeded ${challenges.length} challenges`);
})().catch((e) => { console.error(e); process.exit(1); });
