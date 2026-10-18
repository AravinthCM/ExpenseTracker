package com.example.expensetracker

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.expensetracker.data.preferences.BalancePrivacyPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancePrivacyPreferencesTest {

    @Test
    fun freshPreferencesDefaultToExistingVisibleBehavior() = runBlocking {
        val storeFile = File.createTempFile("balance-privacy", ".preferences_pb")
        storeFile.deleteOnExit()
        val preferences = BalancePrivacyPreferences(
            PreferenceDataStoreFactory.create { storeFile }
        )

        assertTrue(preferences.isBalanceVisible.first())
    }

    @Test
    fun hiddenAndShownValuesPersistAcrossRepositoryInstances() = runBlocking {
        val storeFile = File.createTempFile("balance-privacy", ".preferences_pb")
        storeFile.deleteOnExit()
        val firstStore = PreferenceDataStoreFactory.create { storeFile }
        val firstPreferences = BalancePrivacyPreferences(firstStore)

        firstPreferences.setBalanceVisible(false)
        assertFalse(firstPreferences.isBalanceVisible.first())
        firstPreferences.setBalanceVisible(true)

        val restoredPreferences = BalancePrivacyPreferences(firstStore)
        assertTrue(restoredPreferences.isBalanceVisible.first())
    }
}