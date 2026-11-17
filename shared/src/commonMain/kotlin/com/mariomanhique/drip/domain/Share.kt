package com.mariomanhique.drip.domain

enum class ShareTarget(val label: String) {
    WHATSAPP("WhatsApp"),
    WHATSAPP_STATUS("WhatsApp status"),
    FACEBOOK("Facebook"),
    INSTAGRAM_STORY("Instagram story"),
    INSTAGRAM_FEED("Instagram feed"),
    MORE("More…"),
}

/** What a platform sharer needs: text for the caption plus the data to paint an image card. */
data class ShareContent(
    val headline: String,
    val caption: String,
    val emoji: String,
    val palette: List<Long>,
    val text: String,
    val photoUri: String? = null,
)

/** Sharing opens only once a result is final (RULES.md section 4); outfits stay anonymous until then. */
fun DripState.battleShare(battle: Battle, me: String): ShareContent? {
    if (!battle.closed) return null
    val mine = listOfNotNull(battle.outfitAId, battle.outfitBId).mapNotNull { outfit(it) }
        .firstOrNull { it.authorId == me } ?: return null
    val result = when {
        battle.draw -> "It ended in a draw"
        battle.winnerOutfitId == mine.id -> "I won a showdown"
        else -> "I took on a showdown"
    }
    val total = battle.votes.size
    val headline = "$result on DRIP"
    return ShareContent(
        headline, mine.caption, mine.emoji, mine.palette,
        "$headline — $total anonymous votes decided it. Think you can beat me? ${Rules.APP_LINK}",
        mine.photoUri
    )
}

fun DripState.challengeShare(challengeId: String, me: String): ShareContent? {
    val c = challenges.firstOrNull { it.id == challengeId && it.closed } ?: return null
    val standings = challengeStandings(c.id)
    val idx = standings.indexOfFirst { it.outfit.authorId == me }
    if (idx < 0) return null
    val st = standings[idx]
    val place = if (st.ranked && idx < 3) listOf("1st", "2nd", "3rd")[idx] + " place" else "I entered"
    val headline = "$place in ${c.title} on DRIP"
    return ShareContent(
        headline, st.outfit.caption, st.outfit.emoji, st.outfit.palette,
        "$headline. Ranked by anonymous votes, never by followers. ${Rules.APP_LINK}",
        st.outfit.photoUri
    )
}
