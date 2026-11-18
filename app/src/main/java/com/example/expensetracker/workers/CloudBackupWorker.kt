package com.example.expensetracker.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensetracker.auth.DriveAuthorizationManager
import com.example.expensetracker.data.backup.BackupJsonSerializer
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.cloud.DriveHttpException
import com.example.expensetracker.data.cloud.GoogleDriveRepository
import com.example.expensetracker.data.local.AppDatabase
import com.example.expensetracker.data.preferences.CloudSyncPreferences
import com.example.expensetracker.data.preferences.CloudSyncState
import com.example.expensetracker.data.preferences.GoogleAccountPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val UNIQUE_WORK_NAME = "expense_tracker_auto_backup"
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private val cloudPreferences = CloudSyncPreferences(context.applicationContext)
    private val accountPreferences = GoogleAccountPreferences(context.applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val cloud = cloudPreferences.status.first()
        if (!cloud.enabled) {
            return@withContext Result.success()
        }

        val account = accountPreferences.connectedAccount.first()
        if (account == null || account.googleId != cloud.accountGoogleId) {
            val message = "Sign in to the authorized Google account to resume cloud backup."
            cloudPreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, message)
            return@withContext Result.failure()
        }

        if (!cloudPreferences.updateSyncStateIfEnabled(CloudSyncState.SYNCING)) return@withContext Result.success()
        try {
            val authorization = DriveAuthorizationManager(applicationContext)
            val token = authorization.requireAccessToken(account.email)
            val db = AppDatabase.getDatabase(applicationContext)
            val snapshot = BackupManager(db).createBackupSnapshot(appVersion = "1.0")
            val json = BackupJsonSerializer.serialize(snapshot)
            GoogleDriveRepository(cloudPreferences).uploadBackup(json, token) {
                authorization.requireAccessToken(account.email)
            }
            cloudPreferences.markSyncedIfEnabled(account.googleId, System.currentTimeMillis())
            Result.success()
        } catch (e: DriveAuthorizationManager.ResolutionRequired) {
            cloudPreferences.updateSyncStateIfEnabled(
                CloudSyncState.ERROR,
                "Cloud backup needs your approval. Open Settings and reauthorize Google Drive."
            )
            Result.failure()
        } catch (e: DriveAuthorizationManager.ReauthorizationRequired) {
            cloudPreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, "Google Drive authorization expired. Reauthorize cloud backup in Settings.")
            Result.failure()
        } catch (e: Exception) {
            val recoverable = isRecoverable(e)
            val message = if (recoverable && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                "Cloud backup failed temporarily; retry ${runAttemptCount + 1} of $MAX_RETRY_ATTEMPTS is queued."
            } else {
                e.localizedMessage ?: "Cloud backup failed. Open Settings to check authorization and try again."
            }
            cloudPreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, message)
            if (recoverable && runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun isRecoverable(error: Throwable): Boolean = when (error) {
        is DriveHttpException -> error.statusCode == 429 || error.statusCode >= 500
        is IOException -> true
        else -> error.cause?.let(::isRecoverable) ?: false
    }
}
