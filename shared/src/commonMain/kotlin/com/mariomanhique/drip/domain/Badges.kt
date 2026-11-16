package com.mariomanhique.drip.domain

enum class Rarity(val label: String) { COMMON("Common"), RARE("Rare"), EPIC("Epic"), LEGENDARY("Legendary") }

data class UserStats(
    val posts: Int,
    val postsByCategory: Map<Category, Int>,
    val propsReceived: Int,
    val maxPropsOnOutfit: Int,
    val battleWins: Int,
    val battleLosses: Int,
    val challengeEntries: Int,
    val challengeWins: Int,
    val points: Int,
    val battleDraws: Int = 0,
    /** Elo-style rating, null until enough rated battles. Not influenced by followers. */
    val dripScore: Int? = null,
)

enum class Badge(
    val title: String,
    val emoji: String,
    val rarity: Rarity,
    val criteria: String,
    private val earned: (UserStats) -> Boolean,
) {
    DEBUT("Debut", "📸", Rarity.COMMON, "Publish your first outfit", { it.posts >= 1 }),
    FIRST_VICTORY("First Victory", "🥇", Rarity.COMMON, "Win a 1v1 battle", { it.battleWins >= 1 }),
    STYLE_STAR("Style Star", "⭐", Rarity.RARE, "Receive 10 Fire in total", { it.propsReceived >= 10 }),
    TRENDSETTER("Trendsetter", "🔥", Rarity.RARE, "Get 5 Fire on a single outfit", { it.maxPropsOnOutfit >= 5 }),
    ACCESSORY_MASTER(
        "Accessory Master", "⌚", Rarity.RARE, "Post 3 accessory outfits",
        { (it.postsByCategory[Category.ACCESSORIES] ?: 0) >= 3 },
    ),
    COLOUR_MASTER(
        "Colour Master", "🎨", Rarity.RARE, "Post 3 colour outfits",
        { (it.postsByCategory[Category.COLOUR] ?: 0) >= 3 },
    ),
    CHALLENGE_MASTER("Challenge Master", "🏅", Rarity.EPIC, "Enter 3 challenges", { it.challengeEntries >= 3 }),
    WEEKLY_CHAMPION("Weekly Champion", "👑", Rarity.EPIC, "Win a challenge", { it.challengeWins >= 1 }),
    DRIP_CHAMPION("Drip Champion", "💧", Rarity.LEGENDARY, "Earn 100 Drip Points", { it.points >= 100 });

    fun isEarnedBy(stats: UserStats): Boolean = earned(stats)
}

fun DripState.statsFor(userId: String): UserStats {
    val mine = outfits.filter { it.authorId == userId && !it.hidden }
    val myIds = mine.map { it.id }.toSet()
    val decided = battles.filter { it.closed && (it.outfitAId in myIds || it.outfitBId in myIds) }
    val wins = decided.count { it.winnerOutfitId in myIds }
    val challengeWins = challenges.count { c ->
        c.closed && challengeStandings(c.id).firstOrNull()?.let { it.ranked && it.outfit.authorId == userId } == true
    }
    return UserStats(
        posts = mine.size,
        postsByCategory = mine.groupingBy { it.category }.eachCount(),
        propsReceived = mine.sumOf { it.props },
        maxPropsOnOutfit = mine.maxOfOrNull { it.props } ?: 0,
        battleWins = wins,
        battleLosses = decided.count { !it.draw && it.winnerOutfitId !in myIds },
        battleDraws = decided.count { it.draw },
        dripScore = user(userId)?.dripScore,
        challengeEntries = mine.mapNotNull { it.challengeId }.toSet().size,
        challengeWins = challengeWins,
        points = points(userId),
    )
}

fun DripState.badgesFor(userId: String): List<Badge> {
    val stats = statsFor(userId)
    return Badge.entries.filter { it.isEarnedBy(stats) }
}
