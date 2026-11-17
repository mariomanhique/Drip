package com.mariomanhique.drip.auth

class AuthUser(val uid: String, val email: String, val displayName: String)

/** Either [user] or [error] is set. */
class AuthResult(val user: AuthUser?, val error: String?)

/**
 * Callback-based so Swift can implement it without Kotlin suspend/value-class interop.
 * Android implements it with the Firebase Android SDK, iOS with the Firebase iOS SDK in Swift.
 */
interface AuthService {
    fun currentUser(): AuthUser?
    fun signUp(email: String, password: String, displayName: String, onResult: (AuthResult) -> Unit)
    fun signIn(email: String, password: String, onResult: (AuthResult) -> Unit)
    fun signInWithGoogle(onResult: (AuthResult) -> Unit)
    fun signOut()
}

/** Used in previews and tests; accepts nothing. */
object NoAuthService : AuthService {
    override fun currentUser(): AuthUser? = null
    override fun signUp(email: String, password: String, displayName: String, onResult: (AuthResult) -> Unit) =
        onResult(AuthResult(null, "Sign-in unavailable"))
    override fun signIn(email: String, password: String, onResult: (AuthResult) -> Unit) =
        onResult(AuthResult(null, "Sign-in unavailable"))
    override fun signInWithGoogle(onResult: (AuthResult) -> Unit) = onResult(AuthResult(null, "Sign-in unavailable"))
    override fun signOut() {}
}
