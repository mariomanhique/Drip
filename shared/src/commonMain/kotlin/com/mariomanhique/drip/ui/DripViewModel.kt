package com.mariomanhique.drip.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mariomanhique.drip.auth.AuthResult
import com.mariomanhique.drip.auth.AuthService
import com.mariomanhique.drip.auth.NoAuthService
import com.mariomanhique.drip.data.DripRepository
import com.mariomanhique.drip.domain.Rules
import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.ReportTarget

enum class Tab(val label: String, val icon: String) {
    FEED("Feed", "🏠"),
    BATTLES("Vote", "⚔️"),
    CHALLENGES("Challenges", "🏆"),
    RANKS("Ranks", "👑"),
    PROFILE("Me", "👤"),
}

sealed interface Screen {
    data class OutfitDetail(val outfitId: String) : Screen
    data class ChallengeDetail(val challengeId: String) : Screen
    data class UserProfile(val userId: String) : Screen
    data class Create(val challengeId: String? = null) : Screen
    data object EditProfile : Screen
    data object Settings : Screen
    data object Moderation : Screen
    data object Rules : Screen
}

class DripViewModel(
    val repo: DripRepository = DripRepository(),
    private val auth: AuthService = NoAuthService,
) : ViewModel() {
    val state = repo.state

    var authBusy by mutableStateOf(false)
        private set

    init {
        auth.currentUser()?.let { repo.linkAccount(it.uid, it.email, it.displayName) }
    }

    private fun onAuth(result: AuthResult) {
        authBusy = false
        val user = result.user
        if (user == null) {
            message = result.error ?: "Sign-in failed"
        } else {
            notify(repo.linkAccount(user.uid, user.email, user.displayName))
        }
    }

    fun signUpEmail(name: String, email: String, password: String, ageConfirmed: Boolean) {
        val error = when {
            name.isBlank() -> "Enter your name"
            !ageConfirmed -> "You must be ${Rules.MIN_AGE} or older to use Drip"
            else -> null
        }
        if (error != null) { message = error; return }
        authBusy = true
        auth.signUp(email.trim(), password, name.trim(), ::onAuth)
    }

    fun signInEmail(email: String, password: String) {
        authBusy = true
        auth.signIn(email.trim(), password, ::onAuth)
    }

    fun signInGoogle(registering: Boolean, ageConfirmed: Boolean) {
        if (registering && !ageConfirmed) { message = "You must be ${Rules.MIN_AGE} or older to use Drip"; return }
        authBusy = true
        auth.signInWithGoogle(::onAuth)
    }

    var tab by mutableStateOf(Tab.FEED)
        private set
    val stack = mutableStateListOf<Screen>()
    var message by mutableStateOf<String?>(null)
        private set

    fun nowMs(): Long = repo.nowMs()

    fun selectTab(tab: Tab) {
        repo.tick()
        this.tab = tab
        stack.clear()
    }

    fun open(screen: Screen) {
        if (stack.lastOrNull() != screen) stack.add(screen)
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun openProfile(userId: String) {
        if (userId == state.value.currentUserId) selectTab(Tab.PROFILE) else open(Screen.UserProfile(userId))
    }

    fun toast(text: String) { message = text }

    fun consumeMessage() {
        message = null
    }

    private fun notify(error: String?, success: String? = null): Boolean {
        message = error ?: success
        return error == null
    }

    fun register(name: String, handle: String, password: String, ageConfirmed: Boolean) =
        notify(repo.register(name, handle, password, ageConfirmed))
    fun login(handle: String, password: String) = notify(repo.login(handle, password))

    fun logout() {
        auth.signOut()
        repo.logout()
        stack.clear()
        tab = Tab.FEED
    }

    fun saveProfile(name: String, bio: String, interests: Set<Category>) {
        if (notify(repo.updateProfile(name, bio, interests), "Profile saved")) back()
    }

    fun publish(caption: String, category: Category, palette: List<Long>, emoji: String, challengeId: String?, constraintOk: Boolean = false, photoUri: String? = null) {
        if (notify(repo.publish(caption, category, palette, emoji, challengeId, constraintOk, photoUri), "Fit dropped 🔥")) back()
    }

    fun startBattle(mine: String) =
        notify(repo.startBattle(mine), "Looking for an opponent — we'll match you with someone fair")

    fun voteChallenge(challengeId: String, winnerId: String, loserId: String) =
        notify(repo.voteChallenge(challengeId, winnerId, loserId), "Vote locked in")

    fun vote(battleId: String, outfitId: String) = notify(repo.vote(battleId, outfitId), "Vote locked in")

    fun report(type: ReportTarget, id: String, reason: String) =
        notify(repo.report(type, id, reason), "Thanks — our moderators will review this")

    fun block(userId: String) {
        repo.toggleBlock(userId)
        notify(null, "Block updated")
    }

    fun closeChallenge(id: String) = notify(repo.closeChallenge(id), "Challenge wrapped, points paid out")
}
