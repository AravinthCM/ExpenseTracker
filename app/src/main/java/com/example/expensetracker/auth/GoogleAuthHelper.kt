package com.example.expensetracker.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.expensetracker.R
import com.example.expensetracker.data.preferences.GoogleAccountInfo
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Thin wrapper around Credential Manager that handles the full
 * Google sign-in / sign-out ceremony and returns domain-level results.
 *
 * Intentionally **not** a ViewModel — it is a plain helper class
 * that receives a [Context] (Activity) at call-time so it can be
 * invoked from a ViewModel coroutine while keeping auth logic
 * out of Composable functions.
 */
class GoogleAuthHelper(private val applicationContext: Context) {

    private val credentialManager = CredentialManager.create(applicationContext)

    companion object {
        private const val TAG = "GoogleAuthHelper"
    }

    sealed class SignInResult {
        data class Success(val account: GoogleAccountInfo) : SignInResult()
        data class Error(val message: String) : SignInResult()
        data object Cancelled : SignInResult()
    }

    /**
     * Launches the Google sign-in bottom sheet.
     *
     * @param activityContext Must be an **Activity** context (required by
     *   Credential Manager to anchor the UI).
     */
    suspend fun signIn(activityContext: Context): SignInResult {
        val webClientId = applicationContext.getString(R.string.google_web_client_id)

        val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val response: GetCredentialResponse =
                credentialManager.getCredential(activityContext, request)

            handleSignInResponse(response)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled sign-in")
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No credentials available", e)
            SignInResult.Error("No Google accounts available on this device. Please add a Google account in device settings.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error: ${e.type}", e)
            SignInResult.Error(mapCredentialError(e))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected sign-in error", e)
            SignInResult.Error("Sign-in failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /** Clears the Credential Manager session state (sign-out). */
    suspend fun signOut(): Result<Unit> {
        return try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-out error", e)
            Result.failure(e)
        }
    }

    // ── Internals ──────────────────────────────────────────────

    private fun handleSignInResponse(response: GetCredentialResponse): SignInResult {
        val credential = response.credential

        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return SignInResult.Error("Unexpected credential type returned by Google sign-in.")
        }

        return try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            val googleId = googleIdTokenCredential.id
            val displayName = googleIdTokenCredential.displayName ?: "Google User"
            val email = googleIdTokenCredential.id // For Google, the id IS the email

            Log.d(TAG, "Sign-in success: $email")

            SignInResult.Success(
                GoogleAccountInfo(
                    googleId = googleId,
                    displayName = displayName,
                    email = email
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Google credential", e)
            SignInResult.Error("Failed to parse Google credentials: ${e.localizedMessage}")
        }
    }

    private fun mapCredentialError(e: GetCredentialException): String {
        return when {
            e.message?.contains("play services", ignoreCase = true) == true ->
                "Google Play Services is unavailable or outdated. Please update it."
            e.message?.contains("configuration", ignoreCase = true) == true ->
                "OAuth configuration error. Please verify your Google Cloud Console setup."
            e.message?.contains("network", ignoreCase = true) == true ->
                "Network error. Please check your internet connection and try again."
            else ->
                "Sign-in failed: ${e.localizedMessage ?: e.type}"
        }
    }
}
