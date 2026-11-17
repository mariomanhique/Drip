package com.mariomanhique.drip.data

import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.DripState
import com.mariomanhique.drip.domain.ReportTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * What the UI needs from a data source. Mutators return an error message, or null on success.
 * The in-memory [DripRepository] answers synchronously; [RemoteRepository] is optimistic and
 * reports server rejections later through [messages].
 */
interface DripStore {
    val state: StateFlow<DripState>
    val messages: Flow<String> get() = emptyFlow()

    fun nowMs(): Long
    fun tick()

    fun register(displayName: String, handle: String, password: String, ageConfirmed: Boolean): String?
    fun login(handle: String, password: String): String?
    fun linkAccount(uid: String, email: String, displayName: String): String?
    fun logout()
    fun updateProfile(displayName: String, bio: String, interests: Set<Category>): String?

    fun toggleFollow(userId: String)
    fun toggleBlock(userId: String)

    fun publish(
        caption: String,
        category: Category,
        palette: List<Long>,
        emoji: String,
        challengeId: String?,
        constraintOk: Boolean = false,
        photoUri: String? = null,
    ): String?

    fun toggleProps(outfitId: String)
    fun toggleSave(outfitId: String)
    fun addComment(outfitId: String, text: String)

    fun startBattle(myOutfitId: String): String?
    fun vote(battleId: String, outfitId: String): String?
    fun voteChallenge(challengeId: String, winnerId: String, loserId: String): String?
    fun resolveBattleReview(battleId: String, valid: Boolean)

    fun closeChallenge(challengeId: String): String?

    fun report(targetType: ReportTarget, targetId: String, reason: String): String?
    fun resolveReport(reportId: String, actioned: Boolean)
}
