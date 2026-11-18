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
    val authorizedAtEpochMillis: Long? = null
)

/** Stores authorization state only. Temporary OAuth access tokens are never persisted. */
class DriveAuthorizationPreferences(context: Context) {
    private val dataStore = context.expenseTrackerDataStore

    private companion object {
        val enabledKey = booleanPreferencesKey("cloud_backup_enabled")
        val accountIdKey = stringPreferencesKey("cloud_backup_google_id")
        val authorizedAtKey = longPreferencesKey("cloud_backup_authorized_at")
    }

    val status: Flow<CloudBackupStatus> = dataStore.data.map { prefs ->
        CloudBackupStatus(
            enabled = prefs[enabledKey] ?: false,
            accountGoogleId = prefs[accountIdKey],
            authorizedAtEpochMillis = prefs[authorizedAtKey]
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
        }
    }
}
