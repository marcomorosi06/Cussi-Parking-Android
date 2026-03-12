package com.cuscus.cussiparking.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuscus.cussiparking.data.SettingsManager
import com.cuscus.cussiparking.repository.CussiParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: CussiParkingRepository,
    val settingsManager: SettingsManager,
    val targetProfileId: String?  // se non null, login/register su questo profilo specifico
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Il profilo target (null = nessun profilo pre-selezionato, mostra campo URL)
    val targetProfile = targetProfileId?.let { settingsManager.getProfile(it) }

    fun performLogin(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val profileId = targetProfileId ?: run {
                _errorMessage.value = "Nessun profilo selezionato."
                _isLoading.value = false
                return@launch
            }
            val result = repository.login(profileId, email, password)
            _isLoading.value = false
            result.onSuccess { onSuccess() }
            result.onFailure { _errorMessage.value = it.message }
        }
    }

    fun performRegister(email: String, password: String, username: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val profileId = targetProfileId ?: run {
                _errorMessage.value = "Nessun profilo selezionato."
                _isLoading.value = false
                return@launch
            }
            val result = repository.register(profileId, email, password, username)
            result.onSuccess {
                performLogin(email, password, onSuccess)
            }
            result.onFailure { error ->
                _isLoading.value = false
                _errorMessage.value = error.message
            }
        }
    }

    class Factory(
        private val repository: CussiParkingRepository,
        private val settingsManager: SettingsManager,
        private val targetProfileId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(repository, settingsManager, targetProfileId) as T
    }
}