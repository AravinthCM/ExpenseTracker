package com.example.expensetracker.ui.viewmodel

import android.app.Application
import android.content.Context
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
                    _uiState.value = signOut.exceptionOrNull()?.let {
                        UiState.Error("Account disconnected locally, but Google sign-out could not be completed.")
                    } ?: UiState.Idle
                }
                .onFailure {
                    _uiState.value = UiState.Error("Could not clear the saved Google account. Please try again.")
                }
        }
    }

    fun dismissError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }
}
