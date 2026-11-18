import { initializeApp } from "firebase-admin/app";
import { FieldValue, Firestore, getFirestore, Transaction } from "firebase-admin/firestore";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import * as rules from "./rules";
import { R, DAY } from "./rules";

initializeApp();
const db: Firestore = getFirestore();
const REGION = "europe-west1";
const opts = { region: REGION };

const uidOf = (req: CallableRequest): string => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return req.auth.uid;
};
const str = (v: unknown, name: string, max = 200): string => {
  if (typeof v !== "string" || v.length === 0 || v.length > max) throw new HttpsError("invalid-argument", `Invalid ${name}`);
  return v;
};
const requireModerator = (req: CallableRequest) => {
  if (!req.auth?.token.moderator) throw new HttpsError("permission-denied", "Moderators only");
};

type Stats = { rating: number; ratedBattles: number; flagged: boolean; votesCast: number; showdownsStarted: number; points: number; followerCount: number; followingCount: number };
const defaultStats = (): Stats => ({ rating: R.START_RATING, ratedBattles: 0, flagged: false, votesCast: 0, showdownsStarted: 0, points: 0, followerCount: 0, followingCount: 0 });

async function related(a: string, b: string, tx?: Transaction): Promise<boolean> {
  const refs = [`follows/${a}_${b}`, `follows/${b}_${a}`, `blocks/${a}_${b}`, `blocks/${b}_${a}`].map((p) => db.doc(p));
  const snaps = tx ? await tx.getAll(...refs) : await db.getAll(...refs);
  return snaps.some((s) => s.exists);
}

/* ---------- Profile ---------- */

export const createProfile = onCall(opts, async (req) => {
  const uid = uidOf(req);
  const handle = str(req.data?.handle, "handle", 20).toLowerCase();
  const displayName = str(req.data?.displayName, "displayName", 40);
  if (!/^[a-z0-9_.]{3,20}$/.test(handle)) throw new HttpsError("invalid-argument", "Handle must be 3-20 letters, digits, _ or .");
  if (req.data?.confirmedAge16 !== true) throw new HttpsError("failed-precondition", `DRIP is for ${R.MIN_AGE}+`);
  const bio = typeof req.data?.bio === "string" ? req.data.bio.slice(0, 160) : "";
  const interests = Array.isArray(req.data?.interests) ? req.data.interests.filter((x: unknown) => typeof x === "string").slice(0, 8) : [];

  await db.runTransaction(async (tx) => {
    const userRef = db.doc(`users/${uid}`);
    const handleRef = db.doc(`handles/${handle}`);
    const [u, h] = await Promise.all([tx.get(userRef), tx.get(handleRef)]);
    if (u.exists) throw new HttpsError("already-exists", "Profile already exists");
    if (h.exists) throw new HttpsError("already-exists", "Handle taken");
    tx.set(handleRef, { uid });
    tx.set(userRef, { handle, displayName, bio, interests, city: "Porto", joinedAt: Date.now() });
    tx.set(db.doc(`userStats/${uid}`), defaultStats());
  });
  return { ok: true };
});

/* ---------- Points / rating ---------- */

async function award(userId: string, points: number, reason: string, now: number) {
  const dayStart = Math.floor(now / DAY) * DAY;
  const snap = await db.collection("pointEvents").where("userId", "==", userId).where("at", ">=", dayStart).get();
  const used = snap.docs.reduce((n, d) => n + (d.get("points") as number), 0);
  const granted = rules.grantedPoints(points, used);
  if (granted <= 0) return;
  await db.collection("pointEvents").add({ userId, points: granted, reason, at: now });
  await db.doc(`userStats/${userId}`).set({ points: FieldValue.increment(granted) }, { merge: true });
}

async function rate(a: string, b: string, scoreA: number) {
  const [sa, sb] = await Promise.all([db.doc(`userStats/${a}`).get(), db.doc(`userStats/${b}`).get()]);
  const ra = (sa.get("rating") as number) ?? R.START_RATING;
  const rb = (sb.get("rating") as number) ?? R.START_RATING;
  await db.doc(`userStats/${a}`).set({ rating: ra + rules.eloDelta(ra, rb, scoreA), ratedBattles: FieldValue.increment(1) }, { merge: true });
  await db.doc(`userStats/${b}`).set({ rating: rb + rules.eloDelta(rb, ra, 1 - scoreA), ratedBattles: FieldValue.increment(1) }, { merge: true });
}

/* ---------- Showdowns ---------- */

export const startBattle = onCall(opts, async (req) => {
  const uid = uidOf(req);
  const outfitId = str(req.data?.outfitId, "outfitId");
  const now = Date.now();
  const outfit = await db.doc(`outfits/${outfitId}`).get();
  if (!outfit.exists || outfit.get("authorId") !== uid || outfit.get("hidden") === true)
    throw new HttpsError("failed-precondition", "Outfit not available");

  const asB = await db.collection("battles").where("outfitBId", "==", outfitId).limit(1).get();
  if (!asB.empty) throw new HttpsError("already-exists", "This outfit already had a showdown");
  const battleRef = db.doc(`battles/${outfitId}`); // one showdown per outfit
  await db.runTransaction(async (tx) => {
    const statsRef = db.doc(`userStats/${uid}`);
    const [existing, stats, recent] = await Promise.all([
      tx.get(battleRef),
      tx.get(statsRef),
      tx.get(db.collection("battles").where("creatorId", "==", uid).where("createdAt", ">=", now - DAY)),
    ]);
    if (existing.exists) throw new HttpsError("already-exists", "This outfit already had a showdown");
    const s = { ...defaultStats(), ...(stats.data() ?? {}) } as Stats;
    if (recent.size >= R.MAX_BATTLES_PER_DAY) throw new HttpsError("resource-exhausted", "Daily showdown limit reached");
    tx.set(battleRef, {
      creatorId: uid, outfitAId: outfitId, outfitBId: null, participants: [uid],
      status: "WAITING", createdAt: now, matchedAt: null, endsAt: null, extended: false,
      winnerOutfitId: null, draw: false, votesA: null, votesB: null,
    });
    tx.set(statsRef, { showdownsStarted: FieldValue.increment(1) }, { merge: true });
  });
  await settle(Date.now());
  return { battleId: outfitId };
});

export const castVote = onCall(opts, async (req) => {
  const uid = uidOf(req);
  const battleId = str(req.data?.battleId, "battleId");
  const outfitId = str(req.data?.outfitId, "outfitId");
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const battleRef = db.doc(`battles/${battleId}`);
    const voteRef = battleRef.collection("votes").doc(uid);
    const [battle, vote, user, stats] = await Promise.all([
      tx.get(battleRef), tx.get(voteRef), tx.get(db.doc(`users/${uid}`)), tx.get(db.doc(`userStats/${uid}`)),
    ]);
    if (!battle.exists || !user.exists) throw new HttpsError("not-found", "Not found");
    if (outfitId !== battle.get("outfitAId") && outfitId !== battle.get("outfitBId")) throw new HttpsError("invalid-argument", "Invalid outfit");
    const participants: string[] = battle.get("participants");
    const rel = (await Promise.all(participants.filter((p) => p !== uid).map((p) => related(uid, p, tx)))).some(Boolean);
    const blocker = rules.voteBlocker({
      userId: uid, now, status: battle.get("status"), endsAt: battle.get("endsAt"), participants,
      alreadyVoted: vote.exists, flagged: stats.get("flagged") === true, joinedAt: user.get("joinedAt"), related: rel,
    });
    if (blocker) throw new HttpsError("failed-precondition", blocker);
    tx.set(voteRef, { voterId: uid, outfitId, at: now });
    tx.set(db.doc(`userStats/${uid}`), { votesCast: FieldValue.increment(1) }, { merge: true });
  });
  return { ok: true };
});

async function recentlyInteracted(a: string, b: string, now: number): Promise<boolean> {
  const since = now - R.INTERACTION_WINDOW;
  for (const [owner, commenter] of [[a, b], [b, a]]) {
    const q = await db.collectionGroup("comments")
      .where("outfitAuthorId", "==", owner).where("authorId", "==", commenter).where("at", ">", since).limit(1).get();
    if (!q.empty) return true;
  }
  return false;
}

async function battledRecently(a: string, b: string, now: number, ignore: Set<string>): Promise<boolean> {
  const q = await db.collection("battles").where("participants", "array-contains", a).where("createdAt", ">", now - R.REMATCH_COOLDOWN).get();
  return q.docs.some((d) => !ignore.has(d.id) && d.get("status") !== "VOID" && d.get("outfitBId") && (d.get("participants") as string[]).includes(b));
}

async function applyClosed(id: string, battle: FirebaseFirestore.DocumentData, out: Extract<rules.CloseOutcome, { kind: "closed" }>, now: number) {
  const [oa, ob] = await Promise.all([db.doc(`outfits/${battle.outfitAId}`).get(), db.doc(`outfits/${battle.outfitBId}`).get()]);
  const userA: string = oa.get("authorId");
  const userB: string = ob.get("authorId");
  const winnerOutfitId = out.winner === "A" ? battle.outfitAId : out.winner === "B" ? battle.outfitBId : null;
  await db.doc(`battles/${id}`).update({ status: "CLOSED", winnerOutfitId, draw: out.draw, votesA: out.votesA, votesB: out.votesB });
  if (out.draw) {
    await award(userA, R.DRAW_POINTS, "Showdown draw", now);
    await award(userB, R.DRAW_POINTS, "Showdown draw", now);
  } else {
    const [w, l] = out.winner === "A" ? [userA, userB] : [userB, userA];
    await award(w, R.WIN_POINTS, "Won a showdown", now);
    await award(l, R.LOSS_POINTS, "Played a showdown", now);
  }
  await rate(userA, userB, out.draw ? 0.5 : out.winner === "A" ? 1 : 0);
}

async function closeBattle(id: string, now: number, skipReview = false) {
  const ref = db.doc(`battles/${id}`);
  const battle = (await ref.get()).data();
  if (!battle) return;
  const votesSnap = await ref.collection("votes").get();
  const votes = votesSnap.docs.map((d) => d.data() as rules.Vote);
  const stats = await Promise.all(votes.map((v) => db.doc(`userStats/${v.voterId}`).get()));
  const flagged = new Set(votes.filter((_, i) => stats[i].get("flagged") === true).map((v) => v.voterId));
  const out = rules.decideClose({ votes, flaggedVoters: flagged, outfitAId: battle.outfitAId, extended: battle.extended, matchedAt: battle.matchedAt ?? battle.createdAt, now, skipReview });
  if (out.kind === "extend") await ref.update({ extended: true, endsAt: out.endsAt });
  else if (out.kind === "void") await ref.update({ status: "VOID" });
  else if (out.kind === "review") await ref.update({ status: "UNDER_REVIEW" });
  else await applyClosed(id, battle, out, now);
}

/** Expires unmatched battles, matches waiting ones, and closes ended voting windows. */
export async function settle(now: number) {
  const waitingSnap = await db.collection("battles").where("status", "==", "WAITING").get();
  let waiting = waitingSnap.docs.map((d) => ({ id: d.id, ...(d.data() as any) }));
  for (const w of waiting.filter((b) => now - b.createdAt >= R.MAX_WAIT_FOR_MATCH)) {
    await db.doc(`battles/${w.id}`).update({ status: "VOID" });
  }
  waiting = waiting.filter((b) => now - b.createdAt < R.MAX_WAIT_FOR_MATCH).sort((a, b) => a.createdAt - b.createdAt || a.id.localeCompare(b.id));

  const ratingOf = new Map<string, number>();
  const rating = async (uid: string) => {
    if (!ratingOf.has(uid)) ratingOf.set(uid, ((await db.doc(`userStats/${uid}`).get()).get("rating") as number) ?? R.START_RATING);
    return ratingOf.get(uid)!;
  };

  const consumed = new Set<string>();
  for (const a of waiting) {
    if (consumed.has(a.id)) continue;
    let best: { b: any; diff: number } | null = null;
    for (const b of waiting) {
      if (b.id === a.id || consumed.has(b.id)) continue;
      const ctx: rules.MatchContext = {
        now,
        related: await related(a.creatorId, b.creatorId),
        recentlyInteracted: await recentlyInteracted(a.creatorId, b.creatorId, now),
        battledRecently: await battledRecently(a.creatorId, b.creatorId, now, new Set([a.id, b.id])),
      };
      const ra = await rating(a.creatorId);
      const rb = await rating(b.creatorId);
      if (!rules.canMatch({ userId: a.creatorId, rating: ra, createdAt: a.createdAt }, { userId: b.creatorId, rating: rb, createdAt: b.createdAt }, ctx)) continue;
      const diff = Math.abs(ra - rb);
      if (!best || diff < best.diff || (diff === best.diff && b.createdAt < best.b.createdAt)) best = { b, diff };
    }
    if (!best) continue;
    consumed.add(a.id);
    consumed.add(best.b.id);
    await db.runTransaction(async (tx) => {
      tx.update(db.doc(`battles/${a.id}`), {
        outfitBId: best!.b.outfitAId, participants: [a.creatorId, best!.b.creatorId],
        status: "VOTING", matchedAt: now, endsAt: now + R.VOTING_WINDOW,
      });
      tx.delete(db.doc(`battles/${best!.b.id}`));
    });
  }

  const ended = await db.collection("battles").where("status", "==", "VOTING").where("endsAt", "<=", now).get();
  for (const d of ended.docs) await closeBattle(d.id, now);
}

export const settleBattles = onSchedule({ ...opts, schedule: "every 5 minutes" }, async () => {
  await settle(Date.now());
});

export const resolveBattleReview = onCall(opts, async (req) => {
  requireModerator(req);
  const battleId = str(req.data?.battleId, "battleId");
  const ref = db.doc(`battles/${battleId}`);
  const snap = await ref.get();
  if (snap.get("status") !== "UNDER_REVIEW") throw new HttpsError("failed-precondition", "Not under review");
  if (req.data?.approve === true) await closeBattle(battleId, Date.now(), true);
  else await ref.update({ status: "VOID" });
  return { ok: true };
});

/* ---------- Challenges ---------- */

export const castChallengeVote = onCall(opts, async (req) => {
  const uid = uidOf(req);
  const challengeId = str(req.data?.challengeId, "challengeId");
  const winnerId = str(req.data?.winnerId, "winnerId");
  const loserId = str(req.data?.loserId, "loserId");
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const pair = [winnerId, loserId].sort().join("_");
    const voteRef = db.doc(`challenges/${challengeId}/votes/${uid}_${pair}`);
    const [challenge, vote, user, stats, w, l] = await Promise.all([
      tx.get(db.doc(`challenges/${challengeId}`)), tx.get(voteRef), tx.get(db.doc(`users/${uid}`)), tx.get(db.doc(`userStats/${uid}`)),
      tx.get(db.doc(`outfits/${winnerId}`)), tx.get(db.doc(`outfits/${loserId}`)),
    ]);
    if (!challenge.exists || !user.exists) throw new HttpsError("not-found", "Not found");
    const side = async (o: FirebaseFirestore.DocumentSnapshot) => {
      if (!o.exists) return null;
      const author: string = o.get("authorId");
      return { challengeId: (o.get("challengeId") as string) ?? null, hidden: o.get("hidden") === true, blockedForVoter: author === uid || (await related(uid, author, tx)) };
    };
    const blocker = rules.challengeVoteBlocker({
      now, closed: challenge.get("closed") === true, flagged: stats.get("flagged") === true, joinedAt: user.get("joinedAt"),
      winnerId, loserId, winner: await side(w), loser: await side(l), challengeId, alreadyJudged: vote.exists,
    });
    if (blocker) throw new HttpsError("failed-precondition", blocker);
    tx.set(voteRef, { voterId: uid, winnerId, loserId, at: now });
    const es = (id: string) => db.doc(`challenges/${challengeId}/entryStats/${id}`);
    tx.set(es(winnerId), { wins: FieldValue.increment(1), appearances: FieldValue.increment(1) }, { merge: true });
    tx.set(es(loserId), { appearances: FieldValue.increment(1) }, { merge: true });
    tx.set(db.doc(`userStats/${uid}`), { votesCast: FieldValue.increment(1) }, { merge: true });
  });
  return { ok: true };
});

const toMs = (v: unknown): number =>
  typeof v === "number" ? v : (v as { toMillis?: () => number })?.toMillis?.() ?? 0;

export const closeChallenge = onCall(opts, async (req) => {
  requireModerator(req);
  const id = str(req.data?.challengeId, "challengeId");
  const ref = db.doc(`challenges/${id}`);
  const c = await ref.get();
  if (!c.exists || c.get("closed") === true) throw new HttpsError("failed-precondition", "Not closable");
  const [entries, votes] = await Promise.all([
    db.collection("outfits").where("challengeId", "==", id).where("hidden", "==", false).get(),
    ref.collection("votes").get(),
  ]);
  const ranked = rules.standings(
    entries.docs.map((d) => ({ id: d.id, createdAt: toMs(d.get("createdAt")) })),
    votes.docs.map((d) => d.data() as rules.CVote),
  );
  const now = Date.now();
  const authors = new Map(entries.docs.map((d) => [d.id, d.get("authorId") as string]));
  for (const [i, s] of ranked.entries()) {
    const pts = i < R.PODIUM_POINTS.length ? R.PODIUM_POINTS[i] : R.CHALLENGE_ENTRY_POINTS;
    await award(authors.get(s.outfitId)!, pts, i < R.PODIUM_POINTS.length ? "Challenge podium" : "Challenge entry", now);
  }
  await ref.update({ closed: true, podium: ranked.slice(0, 3).map((s) => s.outfitId) });
  return { ok: true };
});

/* ---------- Reports ---------- */

export const onReportCreated = onDocumentCreated({ document: "reports/{id}", region: REGION }, async (event) => {
  const outfitId = event.data?.get("outfitId") as string | undefined;
  if (!outfitId) return;
  const open = await db.collection("reports").where("outfitId", "==", outfitId).where("status", "==", "OPEN").get();
  const reporters = new Set(open.docs.map((d) => d.get("reporterId")));
  if (reporters.size >= R.REPORTS_TO_AUTO_HIDE) await db.doc(`outfits/${outfitId}`).update({ hidden: true });
});

/* ---------- Follow counters (kept on userStats so follow edges stay private) ---------- */
export const onFollowWritten = onDocumentWritten({ document: "follows/{pair}", region: REGION }, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!!before === !!after) return;
  const d = after ?? before!;
  const delta = after ? 1 : -1;
  const batch = db.batch();
  batch.set(db.doc(`userStats/${d.followerId}`), { followingCount: FieldValue.increment(delta) }, { merge: true });
  batch.set(db.doc(`userStats/${d.followedId}`), { followerCount: FieldValue.increment(delta) }, { merge: true });
  await batch.commit();
});
