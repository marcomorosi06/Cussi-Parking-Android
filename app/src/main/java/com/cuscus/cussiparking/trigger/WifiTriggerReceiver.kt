package com.cuscus.cussiparking.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cuscus.cussiparking.CussiParkingApplication
import kotlinx.coroutines.*

class WifiTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WifiTriggerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION &&
            intent.action != WifiManager.WIFI_STATE_CHANGED_ACTION) return

        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return

        val connectionInfo = wifiManager.connectionInfo
        val currentSsid = connectionInfo?.ssid?.trim('"') ?: ""
        val wifiState = wifiManager.wifiState

        val isDisconnected = (wifiState == WifiManager.WIFI_STATE_ENABLED && currentSsid.isEmpty())
                || wifiState == WifiManager.WIFI_STATE_DISABLED
                || wifiState == WifiManager.WIFI_STATE_DISABLING

        if (!isDisconnected) return

        val prefs = context.getSharedPreferences("wifi_trigger_prefs", Context.MODE_PRIVATE)
        val lastSsid = prefs.getString("last_connected_ssid", "") ?: ""
        if (lastSsid.isEmpty()) return

        Log.d(TAG, "Disconnessione da SSID=$lastSsid, cerco trigger")

        val app = context.applicationContext as CussiParkingApplication
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val triggers = app.database.triggerDao().getEnabledWifiTriggers(lastSsid)
                Log.d(TAG, "Trigger trovati per SSID=$lastSsid: ${triggers.size}")

                triggers.forEach { trigger ->
                    Log.d(TAG, "Accodamento WorkManager per vehicleId=${trigger.localVehicleId}")

                    val inputData = Data.Builder()
                        .putInt(ParkingLocationWorker.KEY_VEHICLE_ID, trigger.localVehicleId)
                        .putString(ParkingLocationWorker.KEY_VEHICLE_NAME, trigger.vehicleName)
                        .putString(ParkingLocationWorker.KEY_LOCATION_MODE, trigger.locationMode)
                        .putString(ParkingLocationWorker.KEY_TRIGGER_LABEL, "WiFi: ${trigger.label}")
                        .build()

                    val workRequest = OneTimeWorkRequestBuilder<ParkingLocationWorker>()
                        .setInputData(inputData)
                        .build()

                    WorkManager.getInstance(context).enqueue(workRequest)
                }

                prefs.edit().remove("last_connected_ssid").apply()
            } catch (e: Exception) {
                Log.e(TAG, "Eccezione: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}