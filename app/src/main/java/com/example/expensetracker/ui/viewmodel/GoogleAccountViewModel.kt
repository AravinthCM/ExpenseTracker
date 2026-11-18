package com.example.expensetracker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.example.expensetracker.auth.DriveAuthorizationManager
import com.example.expensetracker.data.cloud.DriveHttpException
import com.example.expensetracker.data.cloud.GoogleDriveRepository
import com.example.expensetracker.data.cloud.SyncScheduler
import com.example.expensetracker.data.cloud.WorkManagerSyncScheduler
import com.example.expensetracker.data.backup.BackupJsonSerializer
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.local.AppDatabase
import com.example.expensetracker.data.preferences.DriveAuthorizationPreferences
import com.example.expensetracker.data.preferences.CloudBackupStatus
import com.example.expensetracker.data.preferences.CloudSyncState
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = GoogleAccountPreferences(application)
    private val authHelper = GoogleAuthHelper(application)
    private val driveAuthorizationManager = DriveAuthorizationManager(application)
    private val drivePreferences = DriveAuthorizationPreferences(application)
    private val syncScheduler: SyncScheduler = WorkManagerSyncScheduler(application)
    private val driveRepository = GoogleDriveRepository(drivePreferences)
    private val backupManager = BackupManager(AppDatabase.getDatabase(application))

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
        data class Loading(val action: DriveAction) : DriveUiState
        data class NeedsResolution(val pendingIntent: PendingIntent, val action: DriveAction) : DriveUiState
        data object Denied : DriveUiState
        data class Success(val message: String) : DriveUiState
        data class Error(val message: String) : DriveUiState
    }

    enum class DriveAction { AUTHORIZE, BACK_UP, RESTORE }

    private val _driveUiState = MutableStateFlow<DriveUiState>(DriveUiState.Idle)
    val driveUiState: StateFlow<DriveUiState> = _driveUiState.asStateFlow()
    private val restoreJsonChannel = Channel<String>(Channel.BUFFERED)
    val restoreJson = restoreJsonChannel.receiveAsFlow()
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
        syncScheduler.cancelPendingSync()
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
        startDriveAction(DriveAction.AUTHORIZE)
    }

    fun backUpNow() {
        syncScheduler.cancelPendingSync()
        startDriveAction(DriveAction.BACK_UP)
    }
    fun restoreFromDrive() {
        syncScheduler.cancelPendingSync()
        startDriveAction(DriveAction.RESTORE)
    }

    private fun startDriveAction(action: DriveAction) {
        if (_driveUiState.value is DriveUiState.Loading || _driveUiState.value is DriveUiState.NeedsResolution) return
        val account = connectedAccount.value ?: run {
            _driveUiState.value = DriveUiState.Error("Connect a Google account before using cloud backup.")
            return
        }
        val status = cloudBackupStatus.value
        if (action != DriveAction.AUTHORIZE && (!status.enabled || status.accountGoogleId != account.googleId)) {
            _driveUiState.value = DriveUiState.Error("Enable Cloud Backup authorization before continuing.")
            return
        }
        viewModelScope.launch {
            _driveUiState.value = DriveUiState.Loading(action)
            when (val result = driveAuthorizationManager.authorize(account.email)) {
                is DriveAuthorizationManager.Result.Authorized -> runAction(action, account.googleId, account.email, result.accessToken)
                is DriveAuthorizationManager.Result.NeedsResolution -> _driveUiState.value = DriveUiState.NeedsResolution(result.pendingIntent, action)
                DriveAuthorizationManager.Result.Denied -> {
                    if (action == DriveAction.BACK_UP) drivePreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, "Google Drive authorization was denied.")
                    _driveUiState.value = DriveUiState.Denied
                }
                is DriveAuthorizationManager.Result.Error -> {
                    if (action == DriveAction.BACK_UP) drivePreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, result.message)
                    _driveUiState.value = DriveUiState.Error(result.message)
                }
            }
        }
    }

    fun handleDriveAuthorizationResult(resultCode: Int, data: Intent?) {
        val pending = _driveUiState.value as? DriveUiState.NeedsResolution ?: return
        val account = connectedAccount.value ?: run {
            _driveUiState.value = DriveUiState.Error("Connect a Google account before using cloud backup.")
            return
        }
        viewModelScope.launch {
            if (resultCode != android.app.Activity.RESULT_OK) {
                _driveUiState.value = DriveUiState.Denied
            } else {
                when (val result = driveAuthorizationManager.authorizationFromIntent(data)) {
                    is DriveAuthorizationManager.Result.Authorized -> runAction(pending.action, account.googleId, account.email, result.accessToken)
                    is DriveAuthorizationManager.Result.NeedsResolution -> _driveUiState.value = DriveUiState.NeedsResolution(result.pendingIntent, pending.action)
                    DriveAuthorizationManager.Result.Denied -> {
                        if (pending.action == DriveAction.BACK_UP) drivePreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, "Google Drive authorization was denied.")
                        _driveUiState.value = DriveUiState.Denied
                    }
                    is DriveAuthorizationManager.Result.Error -> {
                        if (pending.action == DriveAction.BACK_UP) drivePreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, result.message)
                        _driveUiState.value = DriveUiState.Error(result.message)
                    }
                }
            }
        }
    }

    private suspend fun runAction(action: DriveAction, accountId: String, email: String, accessToken: String) {
        _driveUiState.value = DriveUiState.Loading(action)
        try {
            when (action) {
                DriveAction.AUTHORIZE -> {
                    withContext(Dispatchers.IO) { drivePreferences.enable(accountId, System.currentTimeMillis()) }
                    drivePreferences.updateSyncState(CloudSyncState.IDLE)
                    _driveUiState.value = DriveUiState.Success("Cloud Backup authorization is enabled.")
                }
                DriveAction.BACK_UP -> {
                    drivePreferences.updateSyncStateIfEnabled(CloudSyncState.SYNCING)
                    val snapshot = backupManager.createBackupSnapshot(appVersion = "1.0")
                    val json = withContext(Dispatchers.IO) { BackupJsonSerializer.serialize(snapshot) }
                    driveRepository.uploadBackup(json, accessToken) {
                        driveAuthorizationManager.requireAccessToken(email)
                    }
                    drivePreferences.markSyncedIfEnabled(accountId, System.currentTimeMillis())
                    _driveUiState.value = DriveUiState.Success("Backup uploaded successfully.")
                }
                DriveAction.RESTORE -> {
                    val (json, _) = driveRepository.downloadBackup(accessToken) {
                        driveAuthorizationManager.requireAccessToken(email)
                    }
                    restoreJsonChannel.send(json)
                    _driveUiState.value = DriveUiState.Success("Backup downloaded. Review the restore preview to continue.")
                }
            }
        } catch (e: DriveAuthorizationManager.ResolutionRequired) {
            _driveUiState.value = DriveUiState.NeedsResolution(e.pendingIntent, action)
        } catch (e: DriveHttpException) {
            if (action == DriveAction.BACK_UP) {
                drivePreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, e.message ?: "Drive request failed.")
            }
            _driveUiState.value = DriveUiState.Error(if (e.statusCode == 404) "No cloud backup was found. Back up your data first." else "Drive request failed: ${e.message}")
        } catch (e: Exception) {
            if (action == DriveAction.BACK_UP) {
                drivePreferences.updateSyncStateIfEnabled(CloudSyncState.ERROR, e.localizedMessage ?: "Cloud backup failed.")
            }
            _driveUiState.value = DriveUiState.Error(e.localizedMessage ?: "Cloud backup failed. Check your connection and try again.")
        }
    }

    fun dismissDriveMessage() {
        if (_driveUiState.value is DriveUiState.Error || _driveUiState.value == DriveUiState.Denied || _driveUiState.value is DriveUiState.Success) {
            _driveUiState.value = DriveUiState.Idle
        }
    }

    fun disableCloudBackup() {
        syncScheduler.cancelPendingSync()
        viewModelScope.launch(Dispatchers.IO) {
            drivePreferences.disable()
            _driveUiState.value = DriveUiState.Idle
        }
    }

    fun retryCloudBackup() {
        val message = cloudBackupStatus.value.lastSyncError.orEmpty()
        if (message.contains("authoriz", ignoreCase = true) ||
            message.contains("approval", ignoreCase = true) ||
            message.contains("sign in", ignoreCase = true)
        ) {
            enableCloudBackup()
        } else {
            syncScheduler.scheduleSync()
        }
    }

    fun dismissError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }
}
