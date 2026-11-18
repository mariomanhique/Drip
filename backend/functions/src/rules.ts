/** Pure port of RULES.md (BattleRules.kt / ChallengeRules.kt). No Firebase imports, so it is fully unit-testable. */

export const HOUR = 3_600_000;
export const DAY = 24 * HOUR;

export const R = {
  MIN_AGE: 16,
  VOTING_WINDOW: 24 * HOUR,
  MAX_VOTING_WINDOW: 72 * HOUR,
  MAX_WAIT_FOR_MATCH: 72 * HOUR,
  QUORUM: 20,
  MIN_VOTER_ACCOUNT_AGE: DAY,
  REMATCH_COOLDOWN: 14 * DAY,
  INTERACTION_WINDOW: 30 * DAY,
  DRAW_MARGIN: 2,
  MAX_BATTLES_PER_DAY: 3,
  SPIKE_SHARE: 0.8,
  SPIKE_WINDOW: 5 * 60_000,
  START_RATING: 1000,
  ELO_K: 32,
  MATCH_RATING_RANGE: 200,
  MATCH_RANGE_STEP: 100,
  MATCH_RANGE_STEP_MS: 12 * HOUR,
  WIN_POINTS: 10,
  DRAW_POINTS: 5,
  LOSS_POINTS: 2,
  CHALLENGE_ENTRY_POINTS: 5,
  DAILY_POINTS_CAP: 100,
  PODIUM_POINTS: [30, 20, 15],
  REPORTS_TO_AUTO_HIDE: 3,
};

export type Vote = { voterId: string; outfitId: string; at: number };

export interface VoteCheck {
  userId: string;
  now: number;
  status: string;
  endsAt: number | null;
  participants: string[];
  alreadyVoted: boolean;
  flagged: boolean;
  joinedAt: number;
  /** True when the voter follows / is followed by / blocks / is blocked by any participant. */
  related: boolean;
}

export function voteBlocker(c: VoteCheck): string | null {
  if (c.status !== "VOTING" || (c.endsAt ?? 0) <= c.now) return "Voting has closed";
  if (c.participants.includes(c.userId)) return "You can't vote in your own showdown";
  if (c.alreadyVoted) return "You already voted";
  if (c.flagged) return "Your account can't vote right now";
  if (c.now - c.joinedAt < R.MIN_VOTER_ACCOUNT_AGE) return "New accounts can vote after 24 hours";
  if (c.related) return "This showdown isn't available to you";
  return null;
}

export interface MatchSide {
  userId: string;
  rating: number;
  createdAt: number;
}

export interface MatchContext {
  now: number;
  related: boolean;
  recentlyInteracted: boolean;
  battledRecently: boolean;
}

export function canMatch(a: MatchSide, b: MatchSide, ctx: MatchContext): boolean {
  if (a.userId === b.userId || ctx.related || ctx.recentlyInteracted || ctx.battledRecently) return false;
  const waited = Math.max(ctx.now - a.createdAt, ctx.now - b.createdAt);
  const range = R.MATCH_RATING_RANGE + R.MATCH_RANGE_STEP * Math.floor(waited / R.MATCH_RANGE_STEP_MS);
  return Math.abs(a.rating - b.rating) <= range;
}

export function hasSpike(times: number[]): boolean {
  const t = [...times].sort((x, y) => x - y);
  const needed = R.SPIKE_SHARE * t.length;
  return t.some((start) => t.filter((x) => x >= start && x - start <= R.SPIKE_WINDOW).length >= needed);
}

export function expected(own: number, other: number): number {
  return 1 / (1 + Math.pow(10, (other - own) / 400));
}

/** Kotlin's roundToInt rounds half up; Math.round matches that. */
export function eloDelta(own: number, other: number, score: number): number {
  return Math.round(R.ELO_K * (score - expected(own, other)));
}

export function grantedPoints(requested: number, usedToday: number): number {
  if (requested <= 0) return 0;
  return Math.min(requested, Math.max(0, R.DAILY_POINTS_CAP - usedToday));
}

export type CloseInput = {
  votes: Vote[];
  flaggedVoters: Set<string>;
  outfitAId: string;
  extended: boolean;
  matchedAt: number;
  now: number;
  skipReview?: boolean;
};

export type CloseOutcome =
  | { kind: "extend"; endsAt: number }
  | { kind: "void" }
  | { kind: "review" }
  | { kind: "closed"; votesA: number; votesB: number; draw: boolean; winner: "A" | "B" | null };

/** Decides a battle whose voting window has ended. Mirrors closeBattle() in BattleRules.kt. */
export function decideClose(i: CloseInput): CloseOutcome {
  const valid = i.votes.filter((v) => !i.flaggedVoters.has(v.voterId));
  if (valid.length < R.QUORUM) {
    if (i.extended) return { kind: "void" };
    const endsAt = i.matchedAt + R.MAX_VOTING_WINDOW;
    return endsAt <= i.now ? { kind: "void" } : { kind: "extend", endsAt };
  }
  if (!i.skipReview && hasSpike(valid.map((v) => v.at))) return { kind: "review" };
  const votesA = valid.filter((v) => v.outfitId === i.outfitAId).length;
  const votesB = valid.length - votesA;
  const draw = Math.abs(votesA - votesB) <= R.DRAW_MARGIN;
  return { kind: "closed", votesA, votesB, draw, winner: draw ? null : votesA > votesB ? "A" : "B" };
}

export type Standing = { outfitId: string; wins: number; appearances: number; createdAt: number };
export type CVote = { winnerId: string; loserId: string };

export function standings(entries: { id: string; createdAt: number }[], votes: CVote[]): Standing[] {
  const ids = new Set(entries.map((e) => e.id));
  const valid = votes.filter((v) => ids.has(v.winnerId) && ids.has(v.loserId));
  const rows = entries.map((e) => ({
    outfitId: e.id,
    createdAt: e.createdAt,
    wins: valid.filter((v) => v.winnerId === e.id).length,
    appearances: valid.filter((v) => v.winnerId === e.id || v.loserId === e.id).length,
  }));
  const ranked = (s: Standing) => s.appearances >= R.QUORUM;
  return rows.sort((a, b) => {
    if (ranked(a) !== ranked(b)) return ranked(a) ? -1 : 1;
    if (ranked(a)) {
      const ra = a.wins / a.appearances;
      const rb = b.wins / b.appearances;
      if (ra !== rb) return rb - ra;
      if (a.appearances !== b.appearances) return b.appearances - a.appearances;
    }
    return a.createdAt - b.createdAt;
  });
}

export interface ChallengeVoteCheck {
  now: number;
  closed: boolean;
  flagged: boolean;
  joinedAt: number;
  winnerId: string;
  loserId: string;
  winner: { challengeId: string | null; hidden: boolean; blockedForVoter: boolean } | null;
  loser: { challengeId: string | null; hidden: boolean; blockedForVoter: boolean } | null;
  challengeId: string;
  alreadyJudged: boolean;
}

export function challengeVoteBlocker(c: ChallengeVoteCheck): string | null {
  if (c.closed) return "Voting has closed";
  if (c.flagged) return "Your account can't vote right now";
  if (c.now - c.joinedAt < R.MIN_VOTER_ACCOUNT_AGE) return "New accounts can vote after 24 hours";
  const w = c.winner;
  const l = c.loser;
  if (!w || !l || c.winnerId === c.loserId || w.challengeId !== c.challengeId || l.challengeId !== c.challengeId)
    return "Invalid pair";
  if (w.hidden || l.hidden || w.blockedForVoter || l.blockedForVoter) return "This pair isn't available to you";
  if (c.alreadyJudged) return "You already judged this pair";
  return null;
}
