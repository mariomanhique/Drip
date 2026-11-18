package com.mariomanhique.drip

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.mariomanhique.drip.auth.AuthResult
import com.mariomanhique.drip.auth.AuthService
import com.mariomanhique.drip.auth.AuthUser
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AndroidAuthService(private val appContext: Context) : AuthService {
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activity = WeakReference<Activity>(null)

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid, email.orEmpty(), displayName.orEmpty())

    private fun run(onResult: (AuthResult) -> Unit, block: suspend () -> FirebaseUser?) {
        scope.launch {
            onResult(
                try {
                    val user = block() ?: error("No user returned")
                    AuthResult(user.toAuthUser(), null)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AuthResult(null, friendly(e))
                },
            )
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException,
        is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "Wrong email or password"
        is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "That email is already registered"
        is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Password is too weak (6+ characters)"
        is androidx.credentials.exceptions.GetCredentialCancellationException -> "Sign-in cancelled"
        is androidx.credentials.exceptions.NoCredentialException -> {
            runCatching {
                activity.get()?.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT)
                        .putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            "Add a Google account to this device, then try again"
        }
        else -> e.message ?: "Sign-in failed"
    }

    override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override fun signUp(email: String, password: String, displayName: String, onResult: (AuthResult) -> Unit) =
        run(onResult) {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user
            user?.updateProfile(
                com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(displayName).build(),
            )?.await()
            auth.currentUser
        }

    override fun signIn(email: String, password: String, onResult: (AuthResult) -> Unit) =
        run(onResult) { auth.signInWithEmailAndPassword(email, password).await().user }

    override fun supportsApple() = false

    override fun signInWithApple(onResult: (AuthResult) -> Unit) =
        onResult(AuthResult(null, "Apple sign-in is only available on iOS"))

    override fun signInWithGoogle(onResult: (AuthResult) -> Unit) = run(onResult) {
        val host = activity.get() ?: error("Screen not ready, try again")
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(appContext.getString(R.string.default_web_client_id))
            .build()
        val response = CredentialManager.create(host)
            .getCredential(host, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = response.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unsupported credential"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
    }

    override fun signOut() {
        auth.signOut()
        scope.launch { runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) } }
    }
}
