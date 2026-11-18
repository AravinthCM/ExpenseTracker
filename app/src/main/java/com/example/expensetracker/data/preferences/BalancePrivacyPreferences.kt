package com.example.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.expenseTrackerDataStore by preferencesDataStore(name = "expense_tracker_preferences")

class BalancePrivacyPreferences(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.expenseTrackerDataStore)

    private val balanceVisibilityKey = booleanPreferencesKey("balance_visibility")

    val isBalanceVisible: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[balanceVisibilityKey] ?: true }

    suspend fun setBalanceVisible(isVisible: Boolean) {
        dataStore.edit { preferences ->
            preferences[balanceVisibilityKey] = isVisible
        }
    }
}