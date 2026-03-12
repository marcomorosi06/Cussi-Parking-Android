package com.cuscus.cussiparking.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuscus.cussiparking.network.Vehicle
import com.cuscus.cussiparking.repository.CussiParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: CussiParkingRepository) : ViewModel() {

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isServerUnreachable = MutableStateFlow(false)
    val isServerUnreachable: StateFlow<Boolean> = _isServerUnreachable.asStateFlow()

    // Lista dei server non raggiungibili (per il banner con i nomi)
    private val _unreachableProfiles = MutableStateFlow<List<String>>(emptyList())
    val unreachableProfiles: StateFlow<List<String>> = _unreachableProfiles.asStateFlow()

    private val _joinResult = MutableStateFlow<Boolean?>(null)
    val joinResult: StateFlow<Boolean?> = _joinResult.asStateFlow()

    fun fetchLocations() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = repository.getLocations()
                _vehicles.value = result.vehicles
                _isServerUnreachable.value = result.showBanner
                _unreachableProfiles.value = result.unreachableProfiles
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun parkVehicle(localId: Int, lat: Double, lng: Double) {
        viewModelScope.launch {
            repository.updateLocation(localId, lat, lng)
            fetchLocations()
        }
    }

    fun addVehicle(name: String, forceLocalOnly: Boolean = false, profileId: String? = null) {
        viewModelScope.launch {
            repository.addVehicle(name, "car", forceLocalOnly, profileId)
            fetchLocations()
        }
    }

    fun deleteVehicle(localId: Int) {
        viewModelScope.launch {
            repository.deleteVehicle(localId)
            fetchLocations()
        }
    }

    fun joinWithCode(code: String, profileId: String) {
        viewModelScope.launch {
            val result = repository.joinWithInviteCode(code, profileId)
            if (result.isSuccess) _joinResult.value = true
            else _errorMessage.value = result.exceptionOrNull()?.message
        }
    }

    fun clearJoinResult() { _joinResult.value = null }

    class Factory(private val repository: CussiParkingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) = HomeViewModel(repository) as T
    }
}