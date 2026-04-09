package com.cuscus.cussiparking.trigger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cuscus.cussiparking.CussiParkingApplication
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class ParkingLocationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ParkingWorker"

        const val KEY_VEHICLE_ID    = "vehicle_id"
        const val KEY_VEHICLE_NAME  = "vehicle_name"
        const val KEY_LOCATION_MODE = "location_mode"
        const val KEY_TRIGGER_LABEL = "trigger_label"
    }

    override suspend fun doWork(): Result {
        val vehicleId    = inputData.getInt(KEY_VEHICLE_ID, -1)
        val vehicleName  = inputData.getString(KEY_VEHICLE_NAME) ?: "Veicolo"
        val locationMode = inputData.getString(KEY_LOCATION_MODE) ?: "last_known"
        val triggerLabel = inputData.getString(KEY_TRIGGER_LABEL) ?: "Trigger"

        Log.d(TAG, "doWork — vehicleId=$vehicleId, mode=$locationMode, trigger=$triggerLabel")

        if (vehicleId == -1) return Result.failure()

        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackground = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Permessi — ACCESS_FINE_LOCATION=$hasFine, ACCESS_BACKGROUND_LOCATION=$hasBackground")

        val app = context.applicationContext as CussiParkingApplication

        val location = when (locationMode) {
            "precise" -> getPreciseLocation(vehicleId, hasBackground, locationMode)
            else      -> getLastKnownLocation(vehicleId, locationMode)
        }

        Log.d(TAG, "Posizione finale: $location")

        if (location == null) {
            Log.e(TAG, "Nessuna posizione disponibile per vehicleId=$vehicleId")
            return Result.failure()
        }

        val result = app.repository.updateLocation(vehicleId, location.first, location.second)
        Log.d(TAG, "updateLocation: isSuccess=${result.isSuccess}")

        val source = when {
            triggerLabel.startsWith("Bluetooth", ignoreCase = true) -> ParkingNotificationHelper.SOURCE_BLUETOOTH
            triggerLabel.startsWith("NFC",        ignoreCase = true) -> ParkingNotificationHelper.SOURCE_NFC
            triggerLabel.startsWith("WiFi",       ignoreCase = true) -> ParkingNotificationHelper.SOURCE_WIFI
            triggerLabel.startsWith("GPS",        ignoreCase = true) -> ParkingNotificationHelper.SOURCE_GPS
            triggerLabel.startsWith("Manuale",    ignoreCase = true) -> ParkingNotificationHelper.SOURCE_MANUAL
            else -> ParkingNotificationHelper.SOURCE_BLUETOOTH
        }
        val displayLabel = if (triggerLabel.contains(":"))
            triggerLabel.substringAfter(":").trim() else triggerLabel

        return if (result.isSuccess) {
            context.sendBroadcast(Intent(TriggerLocationService.ACTION_LOCATION_UPDATED))
            ParkingNotificationHelper.notifySuccess(
                context      = context,
                vehicleId    = vehicleId,
                vehicleName  = vehicleName,
                source       = source,
                triggerLabel = displayLabel
            )
            Log.d(TAG, "Parcheggio automatico completato per $vehicleName")
            Result.success()
        } else {
            Log.e(TAG, "updateLocation fallito: ${result.exceptionOrNull()?.message}")
            ParkingNotificationHelper.notifyFailure(
                context     = context,
                vehicleId   = vehicleId,
                vehicleName = vehicleName,
                reason      = "Impossibile contattare il server. Riproverò automaticamente."
            )
            Result.retry()
        }
    }

    private suspend fun getPreciseLocation(vehicleId: Int, hasBackgroundPerm: Boolean, locationMode: String): Pair<Double, Double>? {
        if (!hasBackgroundPerm) {
            Log.w(TAG, "ACCESS_BACKGROUND_LOCATION non concesso")
            return getLastKnownLocation(vehicleId, locationMode)
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            val loc = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { location ->
                            if (cont.isActive) cont.resume(location) {}
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null) {}
                        }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }

            if (loc != null) {
                Pair(loc.latitude, loc.longitude)
            } else {
                getLastKnownLocation(vehicleId, locationMode)
            }
        } catch (e: SecurityException) {
            getLastKnownLocation(vehicleId, locationMode)
        }
    }

    private suspend fun getLastKnownLocation(vehicleId: Int, locationMode: String): Pair<Double, Double>? {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val priority = if (locationMode == "precise") Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
                val cancellationTokenSource = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
                    .addOnSuccessListener { location ->
                        if (cont.isActive) cont.resume(location) {}
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null) {}
                    }
                cont.invokeOnCancellation { cancellationTokenSource.cancel() }
            }

            if (loc != null) {
                return Pair(loc.latitude, loc.longitude)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException: ${e.message}")
        }

        return try {
            val app = context.applicationContext as CussiParkingApplication
            val vehicle = app.database.vehicleDao().getVehicleById(vehicleId)
            if (vehicle?.lat != null && vehicle.lng != null) {
                Pair(vehicle.lat, vehicle.lng)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}