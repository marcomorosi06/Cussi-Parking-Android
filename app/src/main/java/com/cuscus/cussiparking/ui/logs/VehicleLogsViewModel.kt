package com.cuscus.cussiparking.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuscus.cussiparking.network.LocationLog
import com.cuscus.cussiparking.network.LogMember
import com.cuscus.cussiparking.repository.CussiParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VehicleLogsViewModel(
    private val repository: CussiParkingRepository,
    private val profileId: String,
    private val vehicleServerId: Int,
    val isOwner: Boolean
) : ViewModel() {

    private val _logs = MutableStateFlow<List<LocationLog>>(emptyList())
    val logs: StateFlow<List<LocationLog>> = _logs.asStateFlow()

    private val _members = MutableStateFlow<List<LogMember>>(emptyList())
    val members: StateFlow<List<LogMember>> = _members.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val filterDateFrom = MutableStateFlow<Long?>(null)
    val filterDateTo = MutableStateFlow<Long?>(null)
    val filterUserId = MutableStateFlow<Int?>(null)

    private var currentOffset = 0
    private var hasMore = true

    init {
        loadLogs(reset = true)
    }

    fun loadLogs(reset: Boolean = false) {
        if (reset) {
            currentOffset = 0
            hasMore = true
            _logs.value = emptyList()
        }
        if (!hasMore || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repository.getLogs(
                profileId = profileId,
                vehicleServerId = vehicleServerId,
                limit = 100,
                offset = currentOffset,
                dateFrom = filterDateFrom.value,
                dateTo = filterDateTo.value,
                userId = filterUserId.value
            )

            result.onSuccess { response ->
                val newLogs = response.data ?: emptyList()
                _logs.value = if (reset) newLogs else _logs.value + newLogs
                if (response.members != null) {
                    _members.value = response.members
                }
                hasMore = newLogs.size == 100
                currentOffset += newLogs.size
            }.onFailure {
                _error.value = it.message
            }
            _isLoading.value = false
        }
    }

    fun applyFilters(userId: Int?, dateFrom: Long?, dateTo: Long?) {
        filterUserId.value = userId
        filterDateFrom.value = dateFrom
        filterDateTo.value = dateTo
        loadLogs(reset = true)
    }

    fun toggleSettings(enabled: Boolean, retention: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.toggleLogs(profileId, vehicleServerId, enabled, retention)
            result.onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    class Factory(
        private val repository: CussiParkingRepository,
        private val profileId: String,
        private val vehicleServerId: Int,
        private val isOwner: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VehicleLogsViewModel(repository, profileId, vehicleServerId, isOwner) as T
    }
}