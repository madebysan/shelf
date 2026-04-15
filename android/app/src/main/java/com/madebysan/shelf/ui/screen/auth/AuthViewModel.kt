package com.madebysan.shelf.ui.screen.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madebysan.shelf.service.auth.GoogleAuthManager
import com.madebysan.shelf.service.auth.GoogleUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val user: GoogleUser? = null,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: GoogleAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = authManager.restoreSession()
            if (restored) {
                _uiState.value = AuthUiState(user = authManager.currentUser.value)
            }
        }
    }

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authManager.signIn(activityContext)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState(user = user)
                },
                onFailure = { e ->
                    _uiState.value = AuthUiState(error = e.message ?: "Sign-in failed")
                }
            )
        }
    }
}
