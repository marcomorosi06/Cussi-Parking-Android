package com.cuscus.cussiparking.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuscus.cussiparking.data.SettingsManager
import com.cuscus.cussiparking.network.Member
import com.cuscus.cussiparking.repository.CussiParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.cuscus.cussiparking.R
import com.cuscus.cussiparking.CussiParkingApplication

class VehicleMembersViewModel(
    private val repository: CussiParkingRepository,
    private val settingsManager: SettingsManager,
    val vehicleServerId: Int,
    val vehicleName: String,
    val currentUserId: Int,
    val profileId: String
) : ViewModel() {

    val serverUrl: String
        get() = settingsManager.getProfile(profileId)?.serverUrl ?: ""


    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    private val _isCurrentUserOwner = MutableStateFlow(false)
    val isCurrentUserOwner: StateFlow<Boolean> = _isCurrentUserOwner.asStateFlow()

    init { fetchMembers() }

    fun fetchMembers() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.getMembers(vehicleServerId, profileId)
            _isLoading.value = false
            result.onSuccess {
                _members.value = it
                _isCurrentUserOwner.value = it.any { m -> m.isMe && m.role == "owner" }
            }
            result.onFailure { _feedbackMessage.value = it.message }
        }
    }

    fun addMember(username: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.addMember(vehicleServerId, username, profileId)
            _isLoading.value = false
            result.onSuccess {
                _feedbackMessage.value = CussiParkingApplication.instance.getString(R.string.utente_aggiunto_successo, username)
                fetchMembers()
                onSuccess()
            }
            result.onFailure { _feedbackMessage.value = it.message }
        }
    }

    fun removeMember(member: Member) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.removeMember(vehicleServerId, member.id, profileId)
            _isLoading.value = false
            result.onSuccess {
                _feedbackMessage.value = CussiParkingApplication.instance.getString(R.string.utente_rimosso_successo, member.username)
                fetchMembers()
            }
            result.onFailure { _feedbackMessage.value = it.message }
        }
    }

    fun changeRole(member: Member, newRole: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.changeRole(vehicleServerId, member.id, newRole, profileId)
            _isLoading.value = false
            result.onSuccess { msg -> _feedbackMessage.value = "✓ $msg"; fetchMembers() }
            result.onFailure { _feedbackMessage.value = it.message }
        }
    }

    fun generateInviteCode() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.generateInviteCode(vehicleServerId, profileId)
            _isLoading.value = false
            result.onSuccess { _inviteCode.value = it.code }
            result.onFailure { _feedbackMessage.value = it.message }
        }
    }

    fun clearFeedback() { _feedbackMessage.value = null }
    fun clearInviteCode() { _inviteCode.value = null }

    class Factory(
        private val repository: CussiParkingRepository,
        private val settingsManager: SettingsManager,
        private val vehicleServerId: Int,
        private val vehicleName: String,
        private val currentUserId: Int,
        private val profileId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VehicleMembersViewModel(repository, settingsManager, vehicleServerId, vehicleName, currentUserId, profileId) as T
    }
}