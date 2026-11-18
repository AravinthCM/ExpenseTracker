package com.example.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists lightweight Google account metadata (not tokens) so the
 * "connected" state survives app restarts.
 *
 * Uses the same DataStore file as [BalancePrivacyPreferences] to avoid
 * creating a second preferences file.
 */
data class GoogleAccountInfo(
    val googleId: String,
    val displayName: String,
    val email: String
)

class GoogleAccountPreferences(context: Context) {

    private val dataStore = context.expenseTrackerDataStore

    private companion object {
        val KEY_GOOGLE_ID = stringPreferencesKey("google_account_id")
        val KEY_DISPLAY_NAME = stringPreferencesKey("google_display_name")
        val KEY_EMAIL = stringPreferencesKey("google_email")
    }

    /** Emits the saved account info, or null when disconnected. */
    val connectedAccount: Flow<GoogleAccountInfo?> = dataStore.data.map { prefs ->
        val id = prefs[KEY_GOOGLE_ID]
        val name = prefs[KEY_DISPLAY_NAME]
        val email = prefs[KEY_EMAIL]
        if (id != null && name != null && email != null) {
            GoogleAccountInfo(googleId = id, displayName = name, email = email)
        } else {
            null
        }
    }

    suspend fun saveAccount(info: GoogleAccountInfo) {
        dataStore.edit { prefs ->
            prefs[KEY_GOOGLE_ID] = info.googleId
            prefs[KEY_DISPLAY_NAME] = info.displayName
            prefs[KEY_EMAIL] = info.email
        }
    }

    suspend fun clearAccount() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_GOOGLE_ID)
            prefs.remove(KEY_DISPLAY_NAME)
            prefs.remove(KEY_EMAIL)
        }
    }
}
