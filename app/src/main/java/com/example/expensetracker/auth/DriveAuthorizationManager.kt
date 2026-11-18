package com.example.expensetracker.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Handles Drive app-data authorization independently of Google account sign-in. */
class DriveAuthorizationManager(context: Context) {
    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }

    sealed interface Result {
        data class Authorized(val accessToken: String) : Result
        data class NeedsResolution(val pendingIntent: PendingIntent) : Result
        data object Denied : Result
        data class Error(val message: String, val reauthorizationRequired: Boolean = false) : Result
    }

    private val client = Identity.getAuthorizationClient(context.applicationContext)

    suspend fun authorize(accountEmail: String): Result = withContext(Dispatchers.IO) {
        val request = AuthorizationRequest.builder()
            .setAccount(Account(accountEmail, "com.google"))
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        try {
            resultFrom(Tasks.await(client.authorize(request)))
        } catch (e: Exception) {
            mapException(e)
        }
    }

    fun authorizationFromIntent(data: Intent?): Result {
        if (data == null) return Result.Denied
        return try {
            resultFrom(client.getAuthorizationResultFromIntent(data))
        } catch (e: Exception) {
            mapException(e)
        }
    }

    private fun resultFrom(result: AuthorizationResult): Result {
        if (result.hasResolution()) return Result.NeedsResolution(requireNotNull(result.pendingIntent))
        val token = result.accessToken
        return if (!token.isNullOrBlank() && result.grantedScopes.orEmpty().any { it == DRIVE_APPDATA_SCOPE }) {
            Result.Authorized(token)
        } else Result.Denied
    }

    private fun mapException(error: Exception): Result {
        val apiException = error as? ApiException
        return when (apiException?.statusCode) {
            CommonStatusCodes.CANCELED -> Result.Denied
            CommonStatusCodes.SIGN_IN_REQUIRED -> Result.Error("Google authorization expired. Enable cloud backup again to reauthorize.", true)
            else -> Result.Error("Could not authorize Google Drive access. ${error.localizedMessage ?: "Please try again."}")
        }
    }
}
