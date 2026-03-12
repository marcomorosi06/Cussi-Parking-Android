package com.cuscus.cussiparking.trigger

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cuscus.cussiparking.CussiParkingApplication
import kotlinx.coroutines.*

class BluetoothTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BTTriggerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive — action=${intent.action}")

        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return

        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (device == null) {
            Log.e(TAG, "Dispositivo BT null nell'intent")
            return
        }

        val mac = try { device.address } catch (e: SecurityException) {
            Log.e(TAG, "Impossibile leggere MAC (permesso negato)")
            return
        }

        Log.d(TAG, "Dispositivo disconnesso: MAC=$mac")

        val app = context.applicationContext as CussiParkingApplication
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val triggers = app.database.triggerDao().getEnabledBluetoothTriggers(mac)
                Log.d(TAG, "Trigger trovati per MAC=$mac: ${triggers.size}")

                triggers.forEach { trigger ->
                    Log.d(TAG, "Accodamento WorkManager per vehicleId=${trigger.localVehicleId}")

                    // WorkManager invece di startForegroundService:
                    // evita il crash SecurityException su Android 14+ con app in background
                    val inputData = Data.Builder()
                        .putInt(ParkingLocationWorker.KEY_VEHICLE_ID, trigger.localVehicleId)
                        .putString(ParkingLocationWorker.KEY_VEHICLE_NAME, trigger.vehicleName)
                        .putString(ParkingLocationWorker.KEY_LOCATION_MODE, trigger.locationMode)
                        .putString(ParkingLocationWorker.KEY_TRIGGER_LABEL, "Bluetooth: ${trigger.label}")
                        .build()

                    val workRequest = OneTimeWorkRequestBuilder<ParkingLocationWorker>()
                        .setInputData(inputData)
                        .build()

                    WorkManager.getInstance(context).enqueue(workRequest)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Eccezione: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}