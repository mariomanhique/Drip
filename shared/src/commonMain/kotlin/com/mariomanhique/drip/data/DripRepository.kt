package com.mariomanhique.drip.data

import com.mariomanhique.drip.domain.Constraint
import com.mariomanhique.drip.domain.Battle
import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.Comment
import com.mariomanhique.drip.domain.DripState
import com.mariomanhique.drip.domain.Outfit
import com.mariomanhique.drip.domain.PointEvent
import com.mariomanhique.drip.domain.Report
import com.mariomanhique.drip.domain.ReportStatus
import com.mariomanhique.drip.domain.ReportTarget
import com.mariomanhique.drip.domain.Rules
import com.mariomanhique.drip.domain.User
import com.mariomanhique.drip.domain.challengeStandings
import com.mariomanhique.drip.domain.challengeVoteBlocker
import com.mariomanhique.drip.domain.ChallengeVote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.mariomanhique.drip.domain.BattleStatus
import com.mariomanhique.drip.domain.Vote
import com.mariomanhique.drip.domain.award
import com.mariomanhique.drip.domain.closeBattle
import com.mariomanhique.drip.domain.settle
import com.mariomanhique.drip.domain.voteBlocker

/**
 * In-memory source of truth for the MVP. Mutating calls return an error message, or null on success.
 * Swap this class for a networked implementation once a backend exists; the UI only talks to it.
 */
@OptIn(ExperimentalTime::class)
class DripRepository(
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    initial: DripState = Seed.state(now()),
) : DripStore {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<DripState> = _state.asStateFlow()

    private inline fun mutate(block: (DripState) -> Pair<DripState, String?>): String? {
        var error: String? = null
        _state.update { current ->
            val (next, err) = block(current.settle(now()))
            error = err
            if (err == null) next.settle(now()) else next
        }
        return error
    }

    override fun nowMs(): Long = now()

    /** Advances battle matching and settlement; call when the app resumes or a screen opens. */
    override fun tick() = _state.update { it.settle(now()) }

    private fun DripState.fail(message: String) = this to message
    private fun DripState.ok() = this to null
    private fun DripState.id(prefix: String) = "$prefix$nextId"
    private fun DripState.bump() = copy(nextId = nextId + 1)

    // --- Accounts -------------------------------------------------------------------------

    override fun register(displayName: String, handle: String, password: String, ageConfirmed: Boolean): String? = mutate { s ->
        val h = handle.trim().lowercase()
        when {
            displayName.isBlank() -> s.fail("Enter your name")
            !ageConfirmed -> s.fail("You must be ${Rules.MIN_AGE} or older to use Drip")
            h.length < 3 || !h.all { it.isLetterOrDigit() || it == '_' } ->
                s.fail("Handle needs 3+ letters, numbers or _")
            s.users.any { it.handle == h } -> s.fail("That handle is taken")
            password.length < Rules.MIN_PASSWORD -> s.fail("Password needs ${Rules.MIN_PASSWORD}+ characters")
            else -> {
                val user = User(s.id("u"), h, displayName.trim(), joinedOrder = s.users.size + 1, password = password, joinedAt = now())
                s.copy(users = s.users + user, currentUserId = user.id).bump().ok()
            }
        }
    }

    override fun login(handle: String, password: String): String? = mutate { s ->
        val user = s.users.firstOrNull { it.handle == handle.trim().lowercase() && it.password == password }
        if (user == null) s.fail("Wrong handle or password") else s.copy(currentUserId = user.id).ok()
    }

    /** Maps a Firebase identity to a local profile, creating one on first sign-in. */
    override fun linkAccount(uid: String, email: String, displayName: String): String? = mutate { s ->
        if (s.users.any { it.id == uid }) return@mutate s.copy(currentUserId = uid).ok()
        val base = email.substringBefore('@').lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { "user" }
        var handle = base
        var n = 1
        while (s.users.any { it.handle == handle }) handle = base + n++
        val name = displayName.ifBlank { base }
        val user = User(uid, handle, name, joinedOrder = s.users.size + 1, joinedAt = now())
        s.copy(users = s.users + user, currentUserId = uid).ok()
    }

    override fun logout() = _state.update { it.copy(currentUserId = null) }

    override fun updateProfile(displayName: String, bio: String, interests: Set<Category>): String? = mutate { s ->
        val me = s.currentUser ?: return@mutate s.fail("Not signed in")
        if (displayName.isBlank()) return@mutate s.fail("Name can't be empty")
        s.copy(
            users = s.users.map {
                if (it.id == me.id) it.copy(displayName = displayName.trim(), bio = bio.trim(), interests = interests) else it
            },
        ).ok()
    }

    // --- Social graph ---------------------------------------------------------------------

    override fun toggleFollow(userId: String) = _state.update { s ->
        val me = s.currentUserId ?: return@update s
        if (me == userId || s.isBlocked(userId)) return@update s
        val edge = me to userId
        s.copy(follows = if (edge in s.follows) s.follows - edge else s.follows + edge)
    }

    override fun toggleBlock(userId: String) = _state.update { s ->
        val me = s.currentUserId ?: return@update s
        if (me == userId) return@update s
        val edge = me to userId
        if (edge in s.blocks) s.copy(blocks = s.blocks - edge)
        else s.copy(blocks = s.blocks + edge, follows = s.follows - edge - (userId to me))
    }

    // --- Outfits --------------------------------------------------------------------------

    override fun publish(
        caption: String,
        category: Category,
        palette: List<Long>,
        emoji: String,
        challengeId: String?,
        constraintOk: Boolean,
        photoUri: String?,
    ): String? = mutate { s ->
        val me = s.currentUserId ?: return@mutate s.fail("Not signed in")
        if (caption.isBlank()) return@mutate s.fail("Add a caption")
        val challenge = challengeId?.let { id -> s.challenges.firstOrNull { it.id == id } }
        if (challengeId != null) {
            when {
                challenge == null -> return@mutate s.fail("Challenge not found")
                challenge.closed -> return@mutate s.fail("This challenge has ended")
                s.outfits.any { it.challengeId == challengeId && it.authorId == me } ->
                    return@mutate s.fail("You already entered this challenge")
                challenge.constraint != Constraint.OPEN && !constraintOk ->
                    return@mutate s.fail("Confirm the ${challenge.constraint.label} pledge to enter")
            }
        }
        val outfit = Outfit(
            id = s.id("o"), authorId = me, caption = caption.trim(), category = category,
            palette = palette, emoji = emoji, photoUri = photoUri, createdAt = now(), challengeId = challengeId,
            constraintConfirmed = challenge != null && challenge.constraint != Constraint.OPEN,
        )
        s.copy(outfits = s.outfits + outfit).bump().ok()
    }

    private fun updateOutfit(id: String, transform: (Outfit, String) -> Outfit) = _state.update { s ->
        val me = s.currentUserId ?: return@update s
        s.copy(outfits = s.outfits.map { if (it.id == id) transform(it, me) else it })
    }

    override fun toggleProps(outfitId: String) = updateOutfit(outfitId) { o, me ->
        when {
            o.authorId == me -> o
            me in o.propsFrom -> o.copy(propsFrom = o.propsFrom - me)
            else -> o.copy(propsFrom = o.propsFrom + me)
        }
    }

    override fun toggleSave(outfitId: String) = updateOutfit(outfitId) { o, me ->
        o.copy(savedBy = if (me in o.savedBy) o.savedBy - me else o.savedBy + me)
    }

    override fun addComment(outfitId: String, text: String) {
        if (text.isBlank()) return
        _state.update { s ->
            val comment = Comment(s.id("cm"), s.currentUserId ?: return@update s, text.trim(), now())
            s.copy(
                outfits = s.outfits.map { if (it.id == outfitId) it.copy(comments = it.comments + comment) else it },
            ).bump()
        }
    }

    // --- Battles --------------------------------------------------------------------------

    /** Queues the outfit for a showdown; the system picks the opponent (see RULES.md section 3). */
    override fun startBattle(myOutfitId: String): String? = mutate { s ->
        val me = s.currentUserId ?: return@mutate s.fail("Not signed in")
        val mine = s.outfit(myOutfitId)
        when {
            mine == null || mine.hidden -> s.fail("Outfit not found")
            mine.authorId != me -> s.fail("Pick one of your own outfits")
            s.battles.count { b -> now() - b.createdAt < Rules.DAY_MS && listOfNotNull(b.outfitAId, b.outfitBId).any { s.outfit(it)?.authorId == me } } >= Rules.MAX_BATTLES_PER_DAY ->
                s.fail("Daily showdown limit reached (${Rules.MAX_BATTLES_PER_DAY})")
            s.battles.any { it.active && (it.outfitAId == myOutfitId || it.outfitBId == myOutfitId) } ->
                s.fail("This look is already in a showdown")
            else -> s.copy(
                battles = s.battles + Battle(s.id("b"), me, myOutfitId, createdAt = now()),
                users = s.users.map { if (it.id == me) it.copy(showdownsStarted = it.showdownsStarted + 1) else it },
            ).bump().ok()
        }
    }

    override fun vote(battleId: String, outfitId: String): String? = mutate { s ->
        val me = s.currentUserId ?: return@mutate s.fail("Not signed in")
        val battle = s.battles.firstOrNull { it.id == battleId } ?: return@mutate s.fail("Battle not found")
        s.voteBlocker(me, battle, now())?.let { return@mutate s.fail(it) }
        if (outfitId != battle.outfitAId && outfitId != battle.outfitBId) return@mutate s.fail("Invalid choice")
        val updated = battle.copy(votes = battle.votes + Vote(me, outfitId, now()))
        s.copy(
            battles = s.battles.map { if (it.id == battleId) updated else it },
            users = s.users.map { if (it.id == me) it.copy(votesCast = it.votesCast + 1) else it },
        ).ok()
    }

    /** Pairwise challenge pick; [winnerId] is the entry the voter preferred. */
    override fun voteChallenge(challengeId: String, winnerId: String, loserId: String): String? = mutate { s ->
        val me = s.currentUserId ?: return@mutate s.fail("Not signed in")
        s.challengeVoteBlocker(me, challengeId, winnerId, loserId, now())?.let { return@mutate s.fail(it) }
        s.copy(
            challengeVotes = s.challengeVotes + ChallengeVote(me, challengeId, winnerId, loserId, now()),
            users = s.users.map { if (it.id == me) it.copy(votesCast = it.votesCast + 1) else it },
        ).ok()
    }

    /** Moderator decision on a battle flagged for suspicious voting. */
    override fun resolveBattleReview(battleId: String, valid: Boolean) = _state.update { s ->
        val b = s.battles.firstOrNull { it.id == battleId && it.status == BattleStatus.UNDER_REVIEW } ?: return@update s
        if (valid) s.closeBattle(b, now(), skipReview = true)
        else s.copy(battles = s.battles.map { if (it.id == battleId) it.copy(status = BattleStatus.VOID) else it })
    }

    // --- Challenges -----------------------------------------------------------------------

    override fun closeChallenge(challengeId: String): String? = mutate { s ->
        val challenge = s.challenges.firstOrNull { it.id == challengeId } ?: return@mutate s.fail("Challenge not found")
        if (challenge.closed) return@mutate s.fail("Already closed")
        val standings = s.challengeStandings(challengeId)
        val podium = standings.filter { it.ranked }.take(Rules.PODIUM_POINTS.size)
        var next = s.copy(challenges = s.challenges.map { if (it.id == challengeId) it.copy(closed = true) else it })
        standings.forEach { st ->
            val i = podium.indexOf(st)
            val points = if (i >= 0) Rules.PODIUM_POINTS[i] else Rules.CHALLENGE_ENTRY_POINTS
            val reason = if (i >= 0) "${ordinal(i + 1)} place · ${challenge.title}" else "Challenge entry · ${challenge.title}"
            next = next.award(st.outfit.authorId, points, reason, now())
        }
        next.ok()
    }

    private fun ordinal(n: Int) = when (n) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th" }

    // --- Trust & safety -------------------------------------------------------------------

    override fun report(targetType: ReportTarget, targetId: String, reason: String): String? = mutate { s ->
        val me = s.currentUserId ?: return@mutate s.fail("Not signed in")
        if (reason.isBlank()) return@mutate s.fail("Choose a reason")
        if (s.reports.any { it.reporterId == me && it.targetId == targetId && it.status == ReportStatus.OPEN }) {
            return@mutate s.fail("You already reported this")
        }
        val reports = s.reports + Report(s.id("r"), me, targetType, targetId, reason)
        var outfits = s.outfits
        if (targetType == ReportTarget.OUTFIT) {
            val open = reports.count { it.targetId == targetId && it.status == ReportStatus.OPEN }
            if (open >= Rules.REPORTS_TO_AUTO_HIDE) {
                outfits = outfits.map { if (it.id == targetId) it.copy(hidden = true) else it }
            }
        }
        s.copy(reports = reports, outfits = outfits).bump().ok()
    }

    /** Moderator decision: action removes the outfit from circulation; dismiss restores it. */
    override fun resolveReport(reportId: String, actioned: Boolean) = _state.update { s ->
        val report = s.reports.firstOrNull { it.id == reportId } ?: return@update s
        val status = if (actioned) ReportStatus.ACTIONED else ReportStatus.DISMISSED
        val sameTarget = s.reports.filter { it.targetId == report.targetId && it.status == ReportStatus.OPEN }.map { it.id }.toSet()
        s.copy(
            reports = s.reports.map { if (it.id in sameTarget) it.copy(status = status) else it },
            outfits = if (report.targetType == ReportTarget.OUTFIT) {
                s.outfits.map { if (it.id == report.targetId) it.copy(hidden = actioned) else it }
            } else s.outfits,
        )
    }
}
