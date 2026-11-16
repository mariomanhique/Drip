package com.mariomanhique.drip.domain

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure implementation of RULES.md section 3-4. Everything is a function of the state and a clock value. */

fun DripState.authorOf(outfitId: String?): String? = outfit(outfitId ?: return null)?.authorId

fun DripState.participantsOf(b: Battle): List<String> =
    listOfNotNull(authorOf(b.outfitAId), authorOf(b.outfitBId)).ifEmpty { listOf(b.creatorId) }

internal fun DripState.follows(a: String, b: String) = (a to b) in follows || (b to a) in follows
internal fun DripState.blocked(a: String, b: String) = (a to b) in blocks || (b to a) in blocks

private fun DripState.recentlyInteracted(a: String, b: String, now: Long): Boolean = outfits.any { o ->
    val other = when (o.authorId) { a -> b; b -> a; else -> return@any false }
    o.comments.any { it.authorId == other && now - it.at < Rules.INTERACTION_WINDOW_MS }
}

private fun DripState.pairBattledRecently(a: String, b: String, now: Long, ignore: Set<String>): Boolean =
    battles.any { bt ->
        bt.id !in ignore && bt.status != BattleStatus.VOID && bt.outfitBId != null &&
            now - bt.createdAt < Rules.REMATCH_COOLDOWN_MS &&
            participantsOf(bt).toSet() == setOf(a, b)
    }

fun DripState.canMatch(a: Battle, b: Battle, now: Long): Boolean {
    val ua = user(a.creatorId) ?: return false
    val ub = user(b.creatorId) ?: return false
    if (ua.id == ub.id || follows(ua.id, ub.id) || blocked(ua.id, ub.id)) return false
    if (recentlyInteracted(ua.id, ub.id, now)) return false
    if (pairBattledRecently(ua.id, ub.id, now, setOf(a.id, b.id))) return false
    val waited = maxOf(now - a.createdAt, now - b.createdAt)
    val range = Rules.MATCH_RATING_RANGE + Rules.MATCH_RANGE_STEP * (waited / Rules.MATCH_RANGE_STEP_MS).toInt()
    return abs(ua.rating - ub.rating) <= range
}

/** Why [userId] may not vote on [b] right now, or null when eligible. Messages are deliberately generic. */
fun DripState.voteBlocker(userId: String, b: Battle, now: Long): String? {
    val voter = user(userId) ?: return "Not signed in"
    val participants = participantsOf(b)
    return when {
        b.status != BattleStatus.VOTING || (b.endsAt ?: 0) <= now -> "Voting has closed"
        userId in participants -> "You can't vote in your own showdown"
        b.hasVoted(userId) -> "You already voted"
        voter.flagged -> "Your account can't vote right now"
        now - voter.joinedAt < Rules.MIN_VOTER_ACCOUNT_AGE_MS -> "New accounts can vote after 24 hours"
        participants.any { follows(userId, it) || blocked(userId, it) } -> "This showdown isn't available to you"
        else -> null
    }
}

/** Showdowns shown to a voter: random but stable per user (hash order), never ranked by popularity. */
fun DripState.votableBattles(userId: String, now: Long): List<Battle> =
    battles.filter { voteBlocker(userId, it, now) == null }
        .sortedBy { stableHash(userId + it.id) }

private fun stableHash(s: String): Int {
    var h = 17
    for (c in s) h = h * 31 + c.code
    return h xor (h ushr 16)
}

fun DripState.award(userId: String, points: Int, reason: String, now: Long): DripState {
    if (points <= 0) return this
    val today = now / Rules.DAY_MS
    val used = ledger.filter { it.userId == userId && it.at / Rules.DAY_MS == today }.sumOf { it.points }
    val granted = minOf(points, (Rules.DAILY_POINTS_CAP - used).coerceAtLeast(0))
    return if (granted <= 0) this else copy(ledger = ledger + PointEvent(userId, granted, reason, now))
}

private fun expected(own: Int, other: Int) = 1.0 / (1.0 + 10.0.pow((other - own) / 400.0))

private fun DripState.rate(a: String, b: String, scoreA: Double): DripState {
    val ra = user(a)?.rating ?: return this
    val rb = user(b)?.rating ?: return this
    val na = ra + (Rules.ELO_K * (scoreA - expected(ra, rb))).roundToInt()
    val nb = rb + (Rules.ELO_K * ((1 - scoreA) - expected(rb, ra))).roundToInt()
    return copy(
        users = users.map {
            when (it.id) {
                a -> it.copy(rating = na, ratedBattles = it.ratedBattles + 1)
                b -> it.copy(rating = nb, ratedBattles = it.ratedBattles + 1)
                else -> it
            }
        },
    )
}

private fun DripState.replace(b: Battle) = copy(battles = battles.map { if (it.id == b.id) b else it })

private fun hasSpike(valid: List<Vote>): Boolean {
    val times = valid.map { it.at }.sorted()
    val needed = Rules.SPIKE_SHARE * times.size
    return times.indices.any { i -> times.count { it >= times[i] && it - times[i] <= Rules.SPIKE_WINDOW_MS } >= needed }
}

/** Decides a battle whose voting window ended. [skipReview] is used when a moderator cleared a flagged result. */
fun DripState.closeBattle(battle: Battle, now: Long, skipReview: Boolean = false): DripState {
    val bId = battle.outfitBId ?: return replace(battle.copy(status = BattleStatus.VOID))
    val matchedAt = battle.matchedAt ?: battle.createdAt
    val valid = battle.votes.filter { v -> user(v.voterId)?.flagged == false }
    if (valid.size < Rules.QUORUM) {
        return if (!battle.extended) {
            val extended = battle.copy(extended = true, endsAt = matchedAt + Rules.MAX_VOTING_WINDOW_MS)
            if ((extended.endsAt ?: 0) <= now) closeBattle(extended, now) else replace(extended)
        } else replace(battle.copy(status = BattleStatus.VOID))
    }
    if (!skipReview && hasSpike(valid)) return replace(battle.copy(status = BattleStatus.UNDER_REVIEW))

    val votesA = valid.count { it.outfitId == battle.outfitAId }
    val votesB = valid.size - votesA
    val draw = abs(votesA - votesB) <= Rules.DRAW_MARGIN_VOTES
    val winnerOutfit = if (draw) null else if (votesA > votesB) battle.outfitAId else bId
    val userA = authorOf(battle.outfitAId) ?: return replace(battle.copy(status = BattleStatus.VOID))
    val userB = authorOf(bId) ?: return replace(battle.copy(status = BattleStatus.VOID))
    val winnerUser = authorOf(winnerOutfit)

    var s = replace(battle.copy(status = BattleStatus.CLOSED, winnerOutfitId = winnerOutfit, draw = draw))
    s = if (draw) {
        s.award(userA, Rules.BATTLE_DRAW_POINTS, "Showdown draw", now)
            .award(userB, Rules.BATTLE_DRAW_POINTS, "Showdown draw", now)
    } else {
        val loser = if (winnerUser == userA) userB else userA
        s.award(winnerUser!!, Rules.BATTLE_WIN_POINTS, "Won a showdown", now)
            .award(loser, Rules.BATTLE_LOSS_POINTS, "Played a showdown", now)
    }
    val scoreA = if (draw) 0.5 else if (winnerUser == userA) 1.0 else 0.0
    return s.rate(userA, userB, scoreA)
}

/** Advances time: expires unmatched battles, matches waiting ones, and settles ended voting windows. */
fun DripState.settle(now: Long): DripState {
    var s = this

    s = s.copy(battles = s.battles.map {
        if (it.status == BattleStatus.WAITING && now - it.createdAt >= Rules.MAX_WAIT_FOR_MATCH_MS) it.copy(status = BattleStatus.VOID) else it
    })

    // Match oldest waiting battles first; the older battle absorbs the newer one.
    var changed = true
    while (changed) {
        changed = false
        val waiting = s.battles.filter { it.status == BattleStatus.WAITING }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        for (a in waiting) {
            val best = waiting.filter { it.id != a.id && s.canMatch(a, it, now) }
                .minWithOrNull(
                    compareBy<Battle>(
                        { abs((s.user(a.creatorId)?.rating ?: 0) - (s.user(it.creatorId)?.rating ?: 0)) },
                        { it.createdAt }, { it.id },
                    ),
                ) ?: continue
            val matched = a.copy(
                outfitBId = best.outfitAId, status = BattleStatus.VOTING,
                matchedAt = now, endsAt = now + Rules.VOTING_WINDOW_MS,
            )
            s = s.copy(battles = s.battles.filter { it.id != best.id }.map { if (it.id == a.id) matched else it })
            changed = true
            break
        }
    }

    s.battles.filter { it.status == BattleStatus.VOTING }.forEach { b ->
        val current = s.battles.first { it.id == b.id }
        if ((current.endsAt ?: Long.MAX_VALUE) <= now) s = s.closeBattle(current, now)
    }
    return s
}

/** Votes a user still has to cast before they may start another showdown (vote-to-play). */
fun DripState.votesNeeded(userId: String): Int {
    val u = user(userId) ?: return 0
    return (Rules.VOTES_PER_SHOWDOWN * (u.showdownsStarted + 1) - u.votesCast).coerceAtLeast(0)
}

/** Sharing is locked while a showdown is live and opens once the result is final. */
val Battle.shareable: Boolean get() = status == BattleStatus.CLOSED
