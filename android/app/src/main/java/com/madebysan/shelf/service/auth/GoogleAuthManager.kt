package com.madebysan.shelf.service.auth

import android.accounts.Account
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.madebysan.shelf.BuildConfig
import com.madebysan.shelf.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleUser(
    val id: String,
    val displayName: String?,
    val email: String?,
    val idToken: String
)

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {
    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<GoogleUser?>(null)
    val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    // The OAuth2 access token for Drive API calls
    @Volatile
    private var accessToken: String? = null

    private companion object {
        const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.readonly"
    }

    val isSignedIn: Boolean get() = _currentUser.value != null

    suspend fun restoreSession(): Boolean {
        val token = userPreferences.idToken.first()
        val name = userPreferences.userName.first()
        val email = userPreferences.userEmail.first()
        val userId = userPreferences.userId.first()

        return if (token != null && userId != null && email != null) {
            _currentUser.value = GoogleUser(
                id = userId,
                displayName = name,
                email = email,
                idToken = token
            )
            // Fetch a fresh access token for Drive API
            refreshAccessToken(email)
            true
        } else {
            false
        }
    }

    suspend fun signIn(activityContext: Context): Result<GoogleUser> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

            val email = googleIdTokenCredential.id
            val user = GoogleUser(
                id = email,
                displayName = googleIdTokenCredential.displayName,
                email = email,
                idToken = googleIdTokenCredential.idToken
            )

            userPreferences.saveUser(user.id, user.displayName, user.email, user.idToken)
            _currentUser.value = user

            // Get an access token with Drive scope
            refreshAccessToken(email)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) { }
        accessToken = null
        userPreferences.clearUser()
        _currentUser.value = null
    }

    /**
     * Returns the OAuth2 access token for Drive API.
     * If expired, fetches a fresh one.
     */
    suspend fun getAccessToken(): String? {
        val token = accessToken
        if (token != null) return token

        // Try to refresh
        val email = _currentUser.value?.email ?: return null
        return refreshAccessToken(email)
    }

    private suspend fun refreshAccessToken(email: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val account = Account(email, "com.google")
                val token = GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
                accessToken = token
                token
            } catch (e: Exception) {
                accessToken = null
                null
            }
        }
    }

    /**
     * Called by the auth interceptor when a request gets 401.
     * Clears the cached token and fetches a fresh one.
     */
    suspend fun invalidateAndRefreshToken(): String? {
        val email = _currentUser.value?.email ?: return null
        val oldToken = accessToken
        if (oldToken != null) {
            withContext(Dispatchers.IO) {
                GoogleAuthUtil.clearToken(context, oldToken)
            }
        }
        accessToken = null
        return refreshAccessToken(email)
    }
}
