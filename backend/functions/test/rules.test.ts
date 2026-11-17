import test from "node:test";
import assert from "node:assert/strict";
import * as r from "../src/rules";

const now = 1_000 * r.DAY;
const base: r.VoteCheck = {
  userId: "v", now, status: "VOTING", endsAt: now + r.HOUR, participants: ["a", "b"],
  alreadyVoted: false, flagged: false, joinedAt: now - 2 * r.DAY, related: false,
};

test("voteBlocker", () => {
  assert.equal(r.voteBlocker(base), null);
  assert.ok(r.voteBlocker({ ...base, status: "CLOSED" }));
  assert.ok(r.voteBlocker({ ...base, endsAt: now }));
  assert.ok(r.voteBlocker({ ...base, userId: "a" }));
  assert.ok(r.voteBlocker({ ...base, alreadyVoted: true }));
  assert.ok(r.voteBlocker({ ...base, flagged: true }));
  assert.ok(r.voteBlocker({ ...base, joinedAt: now - r.HOUR }));
  assert.ok(r.voteBlocker({ ...base, related: true }));
});

const side = (userId: string, rating: number, createdAt = now): r.MatchSide => ({ userId, rating, createdAt });
const ctx: r.MatchContext = { now, related: false, recentlyInteracted: false, battledRecently: false };

test("canMatch rating range widens with waiting", () => {
  assert.ok(r.canMatch(side("a", 1000), side("b", 1200), ctx));
  assert.ok(!r.canMatch(side("a", 1000), side("b", 1201), ctx));
  assert.ok(r.canMatch(side("a", 1000, now - 12 * r.HOUR), side("b", 1300, now), ctx));
  assert.ok(!r.canMatch(side("a", 1000), side("a", 1000), ctx));
  assert.ok(!r.canMatch(side("a", 1000), side("b", 1000), { ...ctx, related: true }));
  assert.ok(!r.canMatch(side("a", 1000), side("b", 1000), { ...ctx, recentlyInteracted: true }));
  assert.ok(!r.canMatch(side("a", 1000), side("b", 1000), { ...ctx, battledRecently: true }));
});

const votes = (a: number, b: number, spread = 10 * 60_000): r.Vote[] => [
  ...Array.from({ length: a }, (_, i) => ({ voterId: `a${i}`, outfitId: "oa", at: i * spread })),
  ...Array.from({ length: b }, (_, i) => ({ voterId: `b${i}`, outfitId: "ob", at: (a + i) * spread })),
];
const close = (v: r.Vote[], extra: Partial<r.CloseInput> = {}) =>
  r.decideClose({ votes: v, flaggedVoters: new Set(), outfitAId: "oa", extended: false, matchedAt: now - r.DAY, now, ...extra });

test("decideClose", () => {
  assert.deepEqual(close(votes(15, 4)), { kind: "extend", endsAt: now - r.DAY + r.R.MAX_VOTING_WINDOW });
  assert.deepEqual(close(votes(15, 4), { extended: true }), { kind: "void" });
  assert.deepEqual(close(votes(15, 4), { matchedAt: now - 3 * r.DAY }), { kind: "void" });
  assert.deepEqual(close(votes(14, 6)), { kind: "closed", votesA: 14, votesB: 6, draw: false, winner: "A" });
  assert.deepEqual(close(votes(4, 16)), { kind: "closed", votesA: 4, votesB: 16, draw: false, winner: "B" });
  assert.deepEqual(close(votes(11, 9)), { kind: "closed", votesA: 11, votesB: 9, draw: true, winner: null });
  assert.equal((close(votes(12, 8)) as { draw: boolean }).draw, false);
  const flagged = new Set(["a0", "a1"]);
  assert.equal(close(votes(10, 10), { flaggedVoters: flagged }).kind, "extend");
});

test("spike sends result to review unless cleared", () => {
  const v = votes(14, 6, 1000);
  assert.equal(close(v).kind, "review");
  assert.equal(close(v, { skipReview: true }).kind, "closed");
});

test("elo and points cap", () => {
  assert.equal(r.eloDelta(1000, 1000, 1), 16);
  assert.equal(r.eloDelta(1000, 1000, 0), -16);
  assert.equal(r.eloDelta(1000, 1000, 0.5), 0);
  assert.equal(r.grantedPoints(10, 95), 5);
  assert.equal(r.grantedPoints(10, 100), 0);
  assert.equal(r.grantedPoints(0, 0), 0);
  assert.equal(r.votesNeeded(0, 0), 10);
  assert.equal(r.votesNeeded(1, 15), 5);
  assert.equal(r.votesNeeded(0, 99), 0);
});

test("standings rank only after quorum appearances", () => {
  const entries = [{ id: "x", createdAt: 1 }, { id: "y", createdAt: 2 }, { id: "z", createdAt: 3 }];
  const vs: r.CVote[] = [];
  for (let i = 0; i < 20; i++) vs.push({ winnerId: "y", loserId: "x" });
  for (let i = 0; i < 3; i++) vs.push({ winnerId: "z", loserId: "q" });
  const s = r.standings(entries, vs);
  assert.deepEqual(s.map((e) => e.outfitId), ["y", "x", "z"]);
  assert.equal(s[2].appearances, 0);
});

test("challengeVoteBlocker", () => {
  const ok = { challengeId: "c", hidden: false, blockedForVoter: false };
  const c: r.ChallengeVoteCheck = {
    now, closed: false, flagged: false, joinedAt: now - 2 * r.DAY, winnerId: "w", loserId: "l",
    winner: ok, loser: ok, challengeId: "c", alreadyJudged: false,
  };
  assert.equal(r.challengeVoteBlocker(c), null);
  assert.ok(r.challengeVoteBlocker({ ...c, closed: true }));
  assert.ok(r.challengeVoteBlocker({ ...c, loserId: "w" }));
  assert.ok(r.challengeVoteBlocker({ ...c, loser: { ...ok, challengeId: "d" } }));
  assert.ok(r.challengeVoteBlocker({ ...c, winner: { ...ok, blockedForVoter: true } }));
  assert.ok(r.challengeVoteBlocker({ ...c, alreadyJudged: true }));
});
