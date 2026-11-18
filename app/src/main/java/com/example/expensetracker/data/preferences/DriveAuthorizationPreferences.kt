package com.example.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CloudBackupStatus(
    val enabled: Boolean = false,
    val accountGoogleId: String? = null,
    val authorizedAtEpochMillis: Long? = null,
    val backupFileId: String? = null,
    val lastBackupModifiedTime: String? = null,
    val syncState: CloudSyncState = CloudSyncState.IDLE,
    val lastSyncError: String? = null,
    val lastSyncedTimestamp: Long? = null
)

enum class CloudSyncState { IDLE, PENDING, SYNCING, SYNCED, ERROR }

/** Stores authorization state only. Temporary OAuth access tokens are never persisted. */
class DriveAuthorizationPreferences(context: Context) {
    private val dataStore = context.expenseTrackerDataStore

    private companion object {
        val enabledKey = booleanPreferencesKey("cloud_backup_enabled")
        val accountIdKey = stringPreferencesKey("cloud_backup_google_id")
        val authorizedAtKey = longPreferencesKey("cloud_backup_authorized_at")
        val backupFileIdKey = stringPreferencesKey("cloud_backup_file_id")
        val lastBackupModifiedTimeKey = stringPreferencesKey("cloud_backup_last_modified")
        val syncStateKey = stringPreferencesKey("cloud_backup_sync_state")
        val lastSyncErrorKey = stringPreferencesKey("cloud_backup_last_error")
        val lastSyncedTimestampKey = longPreferencesKey("cloud_backup_last_synced_at")
    }

    val status: Flow<CloudBackupStatus> = dataStore.data.map { prefs ->
        CloudBackupStatus(
            enabled = prefs[enabledKey] ?: false,
            accountGoogleId = prefs[accountIdKey],
            authorizedAtEpochMillis = prefs[authorizedAtKey],
            backupFileId = prefs[backupFileIdKey],
            lastBackupModifiedTime = prefs[lastBackupModifiedTimeKey],
            syncState = runCatching { CloudSyncState.valueOf(prefs[syncStateKey] ?: CloudSyncState.IDLE.name) }
                .getOrDefault(CloudSyncState.IDLE),
            lastSyncError = prefs[lastSyncErrorKey],
            lastSyncedTimestamp = prefs[lastSyncedTimestampKey]
        )
    }

    suspend fun enable(accountGoogleId: String, authorizedAtEpochMillis: Long) {
        dataStore.edit { prefs ->
            prefs[enabledKey] = true
            prefs[accountIdKey] = accountGoogleId
            prefs[authorizedAtKey] = authorizedAtEpochMillis
        }
    }

    suspend fun disable() {
        dataStore.edit { prefs ->
            prefs[enabledKey] = false
            prefs.remove(accountIdKey)
            prefs.remove(authorizedAtKey)
            prefs.remove(backupFileIdKey)
            prefs.remove(lastBackupModifiedTimeKey)
            prefs[syncStateKey] = CloudSyncState.IDLE.name
            prefs.remove(lastSyncErrorKey)
            prefs.remove(lastSyncedTimestampKey)
        }
    }

    suspend fun saveBackupFile(fileId: String, modifiedTime: String?) {
        dataStore.edit { prefs ->
            prefs[backupFileIdKey] = fileId
            if (modifiedTime == null) prefs.remove(lastBackupModifiedTimeKey)
            else prefs[lastBackupModifiedTimeKey] = modifiedTime
        }
    }

    suspend fun clearBackupFileId() {
        dataStore.edit { prefs -> prefs.remove(backupFileIdKey) }
    }

    suspend fun updateSyncState(state: CloudSyncState, error: String? = null) {
        dataStore.edit { prefs ->
            prefs[syncStateKey] = state.name
            if (error == null) prefs.remove(lastSyncErrorKey) else prefs[lastSyncErrorKey] = error
        }
    }

    suspend fun markPendingIfEnabled(accountGoogleId: String): Boolean {
        var marked = false
        dataStore.edit { prefs ->
            if (prefs[enabledKey] == true && prefs[accountIdKey] == accountGoogleId) {
                prefs[syncStateKey] = CloudSyncState.PENDING.name
                prefs.remove(lastSyncErrorKey)
                marked = true
            }
        }
        return marked
    }

    suspend fun updateSyncStateIfEnabled(state: CloudSyncState, error: String? = null): Boolean {
        var updated = false
        dataStore.edit { prefs ->
            if (prefs[enabledKey] == true) {
                prefs[syncStateKey] = state.name
                if (error == null) prefs.remove(lastSyncErrorKey) else prefs[lastSyncErrorKey] = error
                updated = true
            }
        }
        return updated
    }

    suspend fun markSyncedIfEnabled(accountGoogleId: String, timestamp: Long): Boolean {
        var updated = false
        dataStore.edit { prefs ->
            if (prefs[enabledKey] == true && prefs[accountIdKey] == accountGoogleId) {
                prefs[syncStateKey] = CloudSyncState.SYNCED.name
                prefs.remove(lastSyncErrorKey)
                prefs[lastSyncedTimestampKey] = timestamp
                updated = true
            }
        }
        return updated
    }

    suspend fun markSynced(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[syncStateKey] = CloudSyncState.SYNCED.name
            prefs.remove(lastSyncErrorKey)
            prefs[lastSyncedTimestampKey] = timestamp
        }
    }
}

typealias CloudSyncPreferences = DriveAuthorizationPreferences
