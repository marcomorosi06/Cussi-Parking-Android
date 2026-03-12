package com.cuscus.cussiparking.ui.triggers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cuscus.cussiparking.local.TriggerDao
import com.cuscus.cussiparking.local.TriggerEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScannedWifi(val ssid: String)
data class ScannedBluetooth(val name: String, val mac: String)

class VehicleTriggersViewModel(
    private val triggerDao: TriggerDao,
    private val context: Context,
    val vehicleId: Int,
    val vehicleName: String
) : ViewModel() {

    val triggers: StateFlow<List<TriggerEntity>> =
        triggerDao.getTriggersForVehicleFlow(vehicleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- NFC Write mode ---
    // Quando non null, MainActivity deve attivare il foreground dispatch NFC per scrivere il tag
    private val _nfcWritePending = MutableStateFlow<TriggerEntity?>(null)
    val nfcWritePending: StateFlow<TriggerEntity?> = _nfcWritePending.asStateFlow()

    fun requestNfcWrite(trigger: TriggerEntity) { _nfcWritePending.value = trigger }
    fun clearNfcWrite()                          { _nfcWritePending.value = null }

    // --- Scan WiFi ---
    @RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE)
    fun getAvailableWifiNetworks(): List<ScannedWifi> {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java) ?: return emptyList()
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return emptyList()
        return try {
            @Suppress("DEPRECATION")
            val results = wm.scanResults ?: emptyList()
            results.mapNotNull { it.SSID?.trim('"')?.takeIf { s -> s.isNotBlank() } }
                .distinct()
                .sorted()
                .map { ScannedWifi(it) }
        } catch (e: Exception) { emptyList() }
    }

    // Rete WiFi attualmente connessa (suggerimento automatico)
    fun getCurrentWifiSsid(): String? {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return null
        return try {
            val info = wm.connectionInfo ?: return null
            val ssid = info.ssid?.trim('"') ?: return null
            if (ssid.isBlank() || ssid == "<unknown ssid>") null else ssid
        } catch (e: Exception) { null }
    }

    // --- Scan Bluetooth ---
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getPairedBluetoothDevices(): List<ScannedBluetooth> {
        val bm = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        val adapter: BluetoothAdapter = bm.adapter ?: return emptyList()

        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPerm) return emptyList()

        return try {
            adapter.bondedDevices
                ?.map { device: BluetoothDevice ->
                    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                            device.name ?: device.address
                        else device.address
                    } else {
                        @Suppress("MissingPermission") device.name ?: device.address
                    }
                    ScannedBluetooth(name = name, mac = device.address)
                }
                ?.sortedBy { it.name }
                ?: emptyList()
        } catch (e: SecurityException) { emptyList() }
    }

    // --- CRUD ---
    fun addTrigger(
        type: String,
        identifier: String,
        label: String,
        locationMode: String
    ) {
        viewModelScope.launch {
            triggerDao.insertTrigger(
                TriggerEntity(
                    localVehicleId = vehicleId,
                    vehicleName = vehicleName,
                    type = type,
                    identifier = identifier,
                    label = label,
                    locationMode = locationMode,
                    enabled = true
                )
            )
        }
    }

    fun deleteTrigger(trigger: TriggerEntity) {
        viewModelScope.launch { triggerDao.deleteTrigger(trigger.id) }
    }

    fun toggleTrigger(trigger: TriggerEntity) {
        viewModelScope.launch { triggerDao.setEnabled(trigger.id, !trigger.enabled) }
    }

    class Factory(
        private val triggerDao: TriggerDao,
        private val context: Context,
        private val vehicleId: Int,
        private val vehicleName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VehicleTriggersViewModel(triggerDao, context, vehicleId, vehicleName) as T
    }
}