// Seeds 15 test users (Firestore only, no Auth login) with stats and outfits.
// Usage: GOOGLE_APPLICATION_CREDENTIALS=key.json node scripts/seed-users.js [--clean]
const admin = require("firebase-admin");
admin.initializeApp({ projectId: "drip-ae9a5" });
const db = admin.firestore();

const people = [
  ["ines.santos", "Inês Santos"], ["tiago.m", "Tiago Moreira"], ["marta.ribeiro", "Marta Ribeiro"],
  ["joao.pinto", "João Pinto"], ["bea.costa", "Beatriz Costa"], ["rui.alves", "Rui Alves"],
  ["sofia.lima", "Sofia Lima"], ["diogo.fer", "Diogo Ferreira"], ["carla.nunes", "Carla Nunes"],
  ["andre.silva", "André Silva"], ["leonor.g", "Leonor Gomes"], ["miguel.c", "Miguel Carvalho"],
  ["yara.mz", "Yara Machava"], ["kelvin.mz", "Kelvin Sitoe"], ["nadia.p", "Nádia Pereira"],
];
const cats = ["STREETWEAR", "FORMAL", "CASUAL", "MINIMALIST", "VINTAGE", "ACCESSORIES", "COLOUR"];
const emojis = ["🧢", "🤵", "👕", "⬜", "🕰️", "⌚", "🎨"];
const palettes = [[0xFF1B1B1B, 0xFFE63946], [0xFF264653, 0xFF2A9D8F], [0xFFF4A261, 0xFFE76F51], [0xFFEDEDED, 0xFF8D99AE], [0xFF3D405B, 0xFFF2CC8F]];
const captions = ["Sunday vibes in the Ribeira", "Thrifted everything", "Rain-proof and still sharp", "Monochrome mood", "Layers for the Porto wind", "Office to bar"];
const now = Date.now();
const DAY = 86400000;

(async () => {
  const clean = process.argv.includes("--clean");
  const batch = db.batch();
  people.forEach(([handle, name], i) => {
    const uid = `seed_${String(i + 1).padStart(2, "0")}`;
    if (clean) {
      for (const p of [`users/${uid}`, `userStats/${uid}`, `handles/${handle}`, `outfits/o_${uid}_1`, `outfits/o_${uid}_2`]) batch.delete(db.doc(p));
      return;
    }
    batch.set(db.doc(`handles/${handle}`), { uid });
    batch.set(db.doc(`users/${uid}`), {
      handle, displayName: name, bio: "Porto • seeded test user", city: "Porto",
      interests: [cats[i % 7], cats[(i + 3) % 7]], joinedAt: now - (i + 1) * DAY, seeded: true,
    });
    batch.set(db.doc(`userStats/${uid}`), {
      rating: 900 + ((i * 37) % 250), ratedBattles: i % 6, votesCast: 5 + i, showdownsStarted: i % 3,
      points: 10 * i, followerCount: 0, followingCount: 0,
    });
    for (const n of [1, 2]) {
      const k = (i + n) % 7;
      batch.set(db.doc(`outfits/o_${uid}_${n}`), {
        authorId: uid, caption: captions[(i + n) % captions.length], category: cats[k], emoji: emojis[k],
        palette: palettes[(i + n) % palettes.length], createdAt: now - (i * 3 + n) * 3600000,
        challengeId: null, hidden: false, seeded: true,
      });
    }
  });
  await batch.commit();
  console.log(clean ? "Cleaned seed users" : `Seeded ${people.length} users with 2 outfits each`);
})().catch((e) => { console.error(e); process.exit(1); });
