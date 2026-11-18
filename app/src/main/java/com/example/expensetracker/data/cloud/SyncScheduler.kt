package com.example.expensetracker.data.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.expensetracker.data.preferences.CloudSyncPreferences
import com.example.expensetracker.data.preferences.CloudSyncState
import com.example.expensetracker.data.preferences.GoogleAccountPreferences
import com.example.expensetracker.workers.CloudBackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

interface SyncScheduler {
    fun scheduleSync()
    fun cancelPendingSync() = Unit
}

class WorkManagerSyncScheduler(context: Context) : SyncScheduler {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val cloudPreferences = CloudSyncPreferences(appContext)
    private val accountPreferences = GoogleAccountPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schedulingMutex = Mutex()

    override fun scheduleSync() {
        scope.launch {
            schedulingMutex.withLock {
                val cloudState = cloudPreferences.status.first()
                val account = accountPreferences.connectedAccount.first()
                if (!cloudState.enabled || account == null || account.googleId != cloudState.accountGoogleId) return@withLock

                try {
                    if (!cloudPreferences.markPendingIfEnabled(account.googleId)) return@withLock
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
                        .setInitialDelay(5, TimeUnit.SECONDS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                        .build()
                    workManager.enqueueUniqueWork(CloudBackupWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
                } catch (e: Exception) {
                    cloudPreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, e.localizedMessage ?: "Could not queue cloud backup.")
                }
            }
        }
    }

    override fun cancelPendingSync() {
        workManager.cancelUniqueWork(CloudBackupWorker.UNIQUE_WORK_NAME)
        scope.launch {
            if (cloudPreferences.status.first().syncState == CloudSyncState.PENDING) {
                cloudPreferences.updateSyncStateIfEnabled(CloudSyncState.IDLE)
            }
        }
    }
}
