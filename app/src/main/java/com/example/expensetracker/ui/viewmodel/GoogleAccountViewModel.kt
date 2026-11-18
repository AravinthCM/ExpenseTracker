package com.example.expensetracker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.example.expensetracker.auth.DriveAuthorizationManager
import com.example.expensetracker.data.preferences.DriveAuthorizationPreferences
import com.example.expensetracker.data.preferences.CloudBackupStatus
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.auth.GoogleAuthHelper
import com.example.expensetracker.data.preferences.GoogleAccountInfo
import com.example.expensetracker.data.preferences.GoogleAccountPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = GoogleAccountPreferences(application)
    private val authHelper = GoogleAuthHelper(application)
    private val driveAuthorizationManager = DriveAuthorizationManager(application)
    private val drivePreferences = DriveAuthorizationPreferences(application)

    val connectedAccount: StateFlow<GoogleAccountInfo?> = preferences.connectedAccount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    sealed interface DriveUiState {
        data object Idle : DriveUiState
        data object Loading : DriveUiState
        data class NeedsResolution(val pendingIntent: PendingIntent) : DriveUiState
        data object Denied : DriveUiState
        data class Error(val message: String) : DriveUiState
    }

    private val _driveUiState = MutableStateFlow<DriveUiState>(DriveUiState.Idle)
    val driveUiState: StateFlow<DriveUiState> = _driveUiState.asStateFlow()
    val cloudBackupStatus: StateFlow<CloudBackupStatus> = drivePreferences.status.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudBackupStatus()
    )

    fun signIn(activityContext: Context) {
        if (_uiState.value == UiState.Loading) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            when (val result = authHelper.signIn(activityContext)) {
                is GoogleAuthHelper.SignInResult.Success -> runCatching {
                    preferences.saveAccount(result.account)
                }.onSuccess {
                    _uiState.value = UiState.Idle
                }.onFailure {
                    _uiState.value = UiState.Error("Could not save the Google account. Please try again.")
                }
                GoogleAuthHelper.SignInResult.Cancelled -> _uiState.value = UiState.Idle
                is GoogleAuthHelper.SignInResult.Error -> _uiState.value = UiState.Error(result.message)
            }
        }
    }

    fun disconnect() {
        if (_uiState.value == UiState.Loading) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val signOut = withContext(Dispatchers.IO) { authHelper.signOut() }
            runCatching { withContext(Dispatchers.IO) { preferences.clearAccount() } }
                .onSuccess {
                    runCatching { withContext(Dispatchers.IO) { drivePreferences.disable() } }
                    _uiState.value = signOut.exceptionOrNull()?.let {
                        UiState.Error("Account disconnected locally, but Google sign-out could not be completed.")
                    } ?: UiState.Idle
                }
                .onFailure {
                    _uiState.value = UiState.Error("Could not clear the saved Google account. Please try again.")
                }
        }
    }

    fun enableCloudBackup() {
        if (_driveUiState.value == DriveUiState.Loading) return
        val account = connectedAccount.value ?: run {
            _driveUiState.value = DriveUiState.Error("Connect a Google account before enabling cloud backup.")
            return
        }
        viewModelScope.launch {
            _driveUiState.value = DriveUiState.Loading
            handleDriveResult(driveAuthorizationManager.authorize(account.email), account.googleId)
        }
    }

    fun handleDriveAuthorizationResult(resultCode: Int, data: Intent?) {
        val accountId = connectedAccount.value?.googleId ?: run {
            _driveUiState.value = DriveUiState.Error("Connect a Google account before enabling cloud backup.")
            return
        }
        viewModelScope.launch {
            _driveUiState.value = DriveUiState.Loading
            if (resultCode != android.app.Activity.RESULT_OK) {
                _driveUiState.value = DriveUiState.Denied
            } else {
                handleDriveResult(driveAuthorizationManager.authorizationFromIntent(data), accountId)
            }
        }
    }

    private suspend fun handleDriveResult(result: DriveAuthorizationManager.Result, accountId: String) {
        when (result) {
            is DriveAuthorizationManager.Result.NeedsResolution ->
                _driveUiState.value = DriveUiState.NeedsResolution(result.pendingIntent)
            is DriveAuthorizationManager.Result.Authorized -> {
                // The access token is short-lived and deliberately discarded after successful authorization.
                runCatching {
                    withContext(Dispatchers.IO) {
                        drivePreferences.enable(accountId, System.currentTimeMillis())
                    }
                }.onSuccess { _driveUiState.value = DriveUiState.Idle }
                    .onFailure { _driveUiState.value = DriveUiState.Error("Authorization succeeded, but cloud backup settings could not be saved.") }
            }
            DriveAuthorizationManager.Result.Denied -> _driveUiState.value = DriveUiState.Denied
            is DriveAuthorizationManager.Result.Error -> _driveUiState.value = DriveUiState.Error(result.message)
        }
    }

    fun dismissDriveMessage() {
        if (_driveUiState.value is DriveUiState.Error || _driveUiState.value == DriveUiState.Denied) {
            _driveUiState.value = DriveUiState.Idle
        }
    }

    fun disableCloudBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            drivePreferences.disable()
            _driveUiState.value = DriveUiState.Idle
        }
    }

    fun dismissError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }
}
