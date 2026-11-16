package com.mariomanhique.drip.domain

enum class Category(val label: String, val emoji: String) {
    STREETWEAR("Streetwear", "🧢"),
    FORMAL("Formal", "🤵"),
    CASUAL("Casual", "👕"),
    MINIMALIST("Minimalist", "⬜"),
    VINTAGE("Vintage", "🕰️"),
    ACCESSORIES("Accessories", "⌚"),
    COLOUR("Colour", "🎨"),
}

data class User(
    val id: String,
    val handle: String,
    val displayName: String,
    val bio: String = "",
    val interests: Set<Category> = emptySet(),
    val country: String = "",
    val joinedOrder: Int = 0,
    val password: String = "",
    val joinedAt: Long = 0,
    val rating: Int = Rules.START_RATING,
    val ratedBattles: Int = 0,
    val flagged: Boolean = false,
    val votesCast: Int = 0,
    val showdownsStarted: Int = 0,
    val city: String = Rules.LAUNCH_CITY,
) {
    /** Drip Score stays hidden until enough rated battles make it meaningful. */
    val dripScore: Int? get() = if (ratedBattles >= Rules.MIN_RATED_BATTLES_FOR_SCORE) rating else null
}

data class Comment(val id: String, val authorId: String, val text: String, val at: Long = 0)

/** Photo upload needs a platform picker; until then an outfit is rendered from a colour palette + emoji. */
data class Outfit(
    val id: String,
    val authorId: String,
    val caption: String,
    val category: Category,
    val palette: List<Long>,
    val emoji: String,
    val createdAt: Int,
    val challengeId: String? = null,
    val propsFrom: Set<String> = emptySet(),
    val savedBy: Set<String> = emptySet(),
    val comments: List<Comment> = emptyList(),
    val hidden: Boolean = false,
    val constraintConfirmed: Boolean = false,
) {
    val props: Int get() = propsFrom.size
}

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val rules: String,
    val category: Category? = null,
    val sponsor: String? = null,
    val closed: Boolean = false,
    val constraint: Constraint = Constraint.OPEN,
)

/** Division of a challenge. Price never decides a result; the constraint keeps the playing field level. */
enum class Constraint(val label: String, val pledge: String) {
    OPEN("Open", ""),
    BUDGET("Budget", "I spent no more than the budget on this look"),
    THRIFT("Thrift", "Every piece in this look is second-hand"),
    CLOSET("Closet only", "Everything here was already in my closet"),
}

enum class BattleStatus { WAITING, VOTING, CLOSED, VOID, UNDER_REVIEW }

/** Pairwise challenge pick: [winnerId] was preferred over [loserId]. */
data class ChallengeVote(val voterId: String, val challengeId: String, val winnerId: String, val loserId: String, val at: Long)

data class Vote(val voterId: String, val outfitId: String, val at: Long)

data class Battle(
    val id: String,
    val creatorId: String,
    val outfitAId: String,
    val outfitBId: String? = null,
    val status: BattleStatus = BattleStatus.WAITING,
    val createdAt: Long = 0,
    val matchedAt: Long? = null,
    val endsAt: Long? = null,
    val extended: Boolean = false,
    val votes: List<Vote> = emptyList(),
    val winnerOutfitId: String? = null,
    val draw: Boolean = false,
) {
    val closed: Boolean get() = status == BattleStatus.CLOSED
    val active: Boolean get() = status == BattleStatus.WAITING || status == BattleStatus.VOTING || status == BattleStatus.UNDER_REVIEW
    fun hasVoted(userId: String) = votes.any { it.voterId == userId }
}

enum class ReportTarget { OUTFIT, USER }
enum class ReportStatus { OPEN, DISMISSED, ACTIONED }

data class Report(
    val id: String,
    val reporterId: String,
    val targetType: ReportTarget,
    val targetId: String,
    val reason: String,
    val status: ReportStatus = ReportStatus.OPEN,
)

/** Every Drip Point is traceable to an entry here, so the rules stay transparent to users. */
data class PointEvent(val userId: String, val points: Int, val reason: String, val at: Long = 0)

data class DripState(
    val currentUserId: String? = null,
    val users: List<User> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
    val battles: List<Battle> = emptyList(),
    val challengeVotes: List<ChallengeVote> = emptyList(),
    val follows: Set<Pair<String, String>> = emptySet(),
    val blocks: Set<Pair<String, String>> = emptySet(),
    val reports: List<Report> = emptyList(),
    val ledger: List<PointEvent> = emptyList(),
    val nextId: Int = 1000,
) {
    val currentUser: User? get() = users.firstOrNull { it.id == currentUserId }

    fun user(id: String): User? = users.firstOrNull { it.id == id }
    fun outfit(id: String): Outfit? = outfits.firstOrNull { it.id == id }

    fun isBlocked(userId: String): Boolean =
        currentUserId != null && (currentUserId to userId) in blocks

    fun isFollowing(userId: String): Boolean =
        currentUserId != null && (currentUserId to userId) in follows

    fun followers(userId: String): Int = follows.count { it.second == userId }
    fun following(userId: String): Int = follows.count { it.first == userId }

    fun points(userId: String): Int = ledger.filter { it.userId == userId }.sumOf { it.points }

    /** Outfits the current user is allowed to see (not hidden by moderation, not from blocked users). */
    fun visibleOutfits(): List<Outfit> =
        outfits.filter { !it.hidden && !isBlocked(it.authorId) }.sortedByDescending { it.createdAt }
}

object Rules {
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 24 * HOUR_MS

    const val MIN_AGE = 16
    const val LAUNCH_CITY = "Porto"
    const val APP_LINK = "https://drip.app" // placeholder until the real domain exists
    const val MIN_PASSWORD = 6
    const val REPORTS_TO_AUTO_HIDE = 3

    // Battles
    const val VOTING_WINDOW_MS = 24 * HOUR_MS
    const val MAX_VOTING_WINDOW_MS = 72 * HOUR_MS
    const val MAX_WAIT_FOR_MATCH_MS = 72 * HOUR_MS
    const val QUORUM = 20
    const val MIN_VOTER_ACCOUNT_AGE_MS = DAY_MS
    const val VOTES_PER_SHOWDOWN = 10
    const val REMATCH_COOLDOWN_MS = 14 * DAY_MS
    const val INTERACTION_WINDOW_MS = 30 * DAY_MS
    const val DRAW_MARGIN_VOTES = 2
    const val MAX_BATTLES_PER_DAY = 3
    const val SPIKE_SHARE = 0.8
    const val SPIKE_WINDOW_MS = 5 * 60_000L

    // Drip Score (Elo-style rating)
    const val START_RATING = 1000
    const val ELO_K = 32
    const val MIN_RATED_BATTLES_FOR_SCORE = 5
    const val MATCH_RATING_RANGE = 200
    const val MATCH_RANGE_STEP = 100
    const val MATCH_RANGE_STEP_MS = 12 * HOUR_MS

    // Drip Points
    const val BATTLE_WIN_POINTS = 10
    const val BATTLE_DRAW_POINTS = 5
    const val BATTLE_LOSS_POINTS = 2
    const val CHALLENGE_ENTRY_POINTS = 5
    const val DAILY_POINTS_CAP = 100
    val PODIUM_POINTS = listOf(30, 20, 15)

    val communityRules = listOf(
        "Post your own outfits only. No stolen or reposted photos.",
        "Be kind. Critique the styling, never the person.",
        "No nudity, hate, harassment or spam.",
        "Points come only from competition results. Followers and payments never buy points.",
        "You can't vote in battles you are part of, or in battles of people you follow or who follow you.",
        "You must be ${Rules.MIN_AGE} or older to use Drip.",
    )
}
