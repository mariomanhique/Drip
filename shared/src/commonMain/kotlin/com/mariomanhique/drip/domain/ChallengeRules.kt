package com.mariomanhique.drip.domain

/** Pairwise challenge voting and ranking, RULES.md section 5. Fire never enters these calculations. */

data class Standing(val outfit: Outfit, val wins: Int, val appearances: Int) {
    val ranked: Boolean get() = appearances >= Rules.QUORUM
    val winRate: Double get() = if (appearances == 0) 0.0 else wins.toDouble() / appearances
}

private fun DripState.validChallengeVotes(challengeId: String): List<ChallengeVote> =
    challengeVotes.filter { v ->
        v.challengeId == challengeId && outfit(v.winnerId)?.hidden == false && outfit(v.loserId)?.hidden == false
    }

fun DripState.challengeStandings(challengeId: String): List<Standing> {
    val votes = validChallengeVotes(challengeId)
    return outfits.filter { it.challengeId == challengeId && !it.hidden }
        .map { o ->
            Standing(o, votes.count { it.winnerId == o.id }, votes.count { it.winnerId == o.id || it.loserId == o.id })
        }
        .sortedWith(
            compareByDescending<Standing> { it.ranked }
                .thenByDescending { if (it.ranked) it.winRate else 0.0 }
                .thenByDescending { if (it.ranked) it.appearances else 0 }
                .thenBy { it.outfit.createdAt },
        )
}

fun DripState.rankChallenge(challengeId: String): List<Outfit> = challengeStandings(challengeId).map { it.outfit }

private fun DripState.mayJudge(userId: String, o: Outfit): Boolean =
    !o.hidden && o.authorId != userId && !follows(userId, o.authorId) && !blocked(userId, o.authorId)

fun DripState.challengeVoteBlocker(userId: String, challengeId: String, winnerId: String, loserId: String, now: Long): String? {
    val voter = user(userId) ?: return "Not signed in"
    val c = challenges.firstOrNull { it.id == challengeId } ?: return "Challenge not found"
    val w = outfit(winnerId)
    val l = outfit(loserId)
    return when {
        c.closed -> "Voting has closed"
        voter.flagged -> "Your account can't vote right now"
        now - voter.joinedAt < Rules.MIN_VOTER_ACCOUNT_AGE_MS -> "New accounts can vote after 24 hours"
        w == null || l == null || w.id == l.id || w.challengeId != challengeId || l.challengeId != challengeId -> "Invalid pair"
        !mayJudge(userId, w) || !mayJudge(userId, l) -> "This pair isn't available to you"
        challengeVotes.any { it.voterId == userId && setOf(it.winnerId, it.loserId) == setOf(winnerId, loserId) } -> "You already judged this pair"
        else -> null
    }
}

/** Next pair for a voter: least-seen entries first (exposure balancing), random but stable per user. */
fun DripState.nextChallengePair(userId: String, challengeId: String, now: Long): Pair<Outfit, Outfit>? {
    val c = challenges.firstOrNull { it.id == challengeId } ?: return null
    if (c.closed) return null
    val entries = outfits.filter { it.challengeId == challengeId && mayJudge(userId, it) }
    val seen = challengeStandings(challengeId).associate { it.outfit.id to it.appearances }
    var best: Pair<Outfit, Outfit>? = null
    var bestKey = Long.MAX_VALUE
    for (i in entries.indices) for (j in i + 1 until entries.size) {
        val a = entries[i]; val b = entries[j]
        if (challengeVoteBlocker(userId, challengeId, a.id, b.id, now) != null) continue
        val key = ((seen[a.id] ?: 0) + (seen[b.id] ?: 0)) * 100_000L + (stableHashOf(userId + a.id + b.id) and 0xFFFF)
        if (key < bestKey) { bestKey = key; best = a to b }
    }
    return best?.let { (a, b) -> if (stableHashOf(userId + a.id) % 2 == 0) a to b else b to a }
}

private fun stableHashOf(s: String): Int {
    var h = 17
    for (c in s) h = h * 31 + c.code
    return (h xor (h ushr 16)) and Int.MAX_VALUE
}
