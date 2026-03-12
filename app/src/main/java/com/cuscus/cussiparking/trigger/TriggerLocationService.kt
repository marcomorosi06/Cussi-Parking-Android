package com.cuscus.cussiparking.trigger

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.cuscus.cussiparking.CussiParkingApplication
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class TriggerLocationService : Service() {

    companion object {
        private const val TAG = "TriggerLocationService"

        const val EXTRA_VEHICLE_ID    = "vehicle_id"
        const val EXTRA_VEHICLE_NAME  = "vehicle_name"
        const val EXTRA_LOCATION_MODE = "location_mode"  // "precise" | "last_known"
        const val EXTRA_TRIGGER_LABEL = "trigger_label"

        const val CHANNEL_ID = "cussiparking_trigger"

        // Action broadcast per notificare la UI che qualcosa è cambiato
        const val ACTION_LOCATION_UPDATED = "com.cuscus.cussiparking.LOCATION_UPDATED"

        fun buildIntent(
            context: Context,
            vehicleId: Int,
            vehicleName: String,
            locationMode: String,
            triggerLabel: String
        ) = Intent(context, TriggerLocationService::class.java).apply {
            putExtra(EXTRA_VEHICLE_ID, vehicleId)
            putExtra(EXTRA_VEHICLE_NAME, vehicleName)
            putExtra(EXTRA_LOCATION_MODE, locationMode)
            putExtra(EXTRA_TRIGGER_LABEL, triggerLabel)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        ParkingNotificationHelper.createChannels(this)
        Log.d(TAG, "Service creato")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vehicleId    = intent?.getIntExtra(EXTRA_VEHICLE_ID, -1) ?: -1
        val vehicleName  = intent?.getStringExtra(EXTRA_VEHICLE_NAME) ?: "Veicolo"
        val locationMode = intent?.getStringExtra(EXTRA_LOCATION_MODE) ?: "last_known"
        val triggerLabel = intent?.getStringExtra(EXTRA_TRIGGER_LABEL) ?: "Trigger"

        Log.d(TAG, "onStartCommand — vehicleId=$vehicleId, vehicleName=$vehicleName, mode=$locationMode, trigger=$triggerLabel")

        if (vehicleId == -1) {
            Log.e(TAG, "vehicleId non valido (-1), esco")
            stopSelf()
            return START_NOT_STICKY
        }

        // Mostra notifica foreground immediatamente (obbligatorio su Android 8+)
        startForeground(
            vehicleId + 1000,
            ParkingNotificationHelper.buildForegroundNotification(this, "$vehicleName — rilevamento posizione…")
        )

        serviceScope.launch {
            try {
                val app = applicationContext as CussiParkingApplication

                // Tenta prima di ottenere la posizione dal GPS
                var location: Pair<Double, Double>? = when (locationMode) {
                    "precise" -> getPreciseLocation()
                    else      -> getLastKnownLocation()
                }

                Log.d(TAG, "Posizione GPS: $location")

                // Fallback: se il GPS non ha una posizione, usa le coordinate già salvate nel DB locale
                if (location == null) {
                    Log.w(TAG, "GPS non disponibile, uso le coordinate salvate nel DB locale")
                    val vehicle = app.database.vehicleDao().getVehicleById(vehicleId)
                    if (vehicle?.lat != null && vehicle.lng != null) {
                        location = Pair(vehicle.lat, vehicle.lng)
                        Log.d(TAG, "Usando coordinate DB: lat=${vehicle.lat}, lng=${vehicle.lng}")
                    }
                }

                if (location != null) {
                    Log.d(TAG, "Chiamo updateLocation per vehicleId=$vehicleId lat=${location.first} lng=${location.second}")
                    val result = app.repository.updateLocation(vehicleId, location.first, location.second)
                    Log.d(TAG, "updateLocation result: isSuccess=${result.isSuccess}, error=${result.exceptionOrNull()?.message}")

                    if (result.isSuccess) {
                        // Notifica la HomeScreen di ricaricare i dati
                        applicationContext.sendBroadcast(Intent(ACTION_LOCATION_UPDATED))
                        Log.d(TAG, "Broadcast inviato: $ACTION_LOCATION_UPDATED")
                        // Notifica di successo specifica per sorgente
                        val source = when {
                            triggerLabel.startsWith("Bluetooth", ignoreCase = true) -> ParkingNotificationHelper.SOURCE_BLUETOOTH
                            triggerLabel.startsWith("NFC",        ignoreCase = true) -> ParkingNotificationHelper.SOURCE_NFC
                            triggerLabel.startsWith("WiFi",       ignoreCase = true) -> ParkingNotificationHelper.SOURCE_WIFI
                            triggerLabel.startsWith("GPS",        ignoreCase = true) -> ParkingNotificationHelper.SOURCE_GPS
                            else -> ParkingNotificationHelper.SOURCE_MANUAL
                        }
                        val displayLabel = if (triggerLabel.contains(":"))
                            triggerLabel.substringAfter(":").trim() else triggerLabel
                        ParkingNotificationHelper.notifySuccess(applicationContext, vehicleId, vehicleName, source, displayLabel)
                    } else {
                        ParkingNotificationHelper.notifyFailure(applicationContext, vehicleId, vehicleName, "Impossibile salvare la posizione")
                    }
                } else {
                    Log.e(TAG, "Nessuna posizione disponibile (né GPS né DB) per vehicleId=$vehicleId")
                    ParkingNotificationHelper.notifyFailure(applicationContext, vehicleId, vehicleName, "Posizione non disponibile")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                ParkingNotificationHelper.notifyFailure(applicationContext, vehicleId, vehicleName, "Permesso posizione negato")
            } catch (e: Exception) {
                Log.e(TAG, "Eccezione inattesa: ${e.message}", e)
                ParkingNotificationHelper.notifyFailure(applicationContext, vehicleId, vehicleName, "Errore: ${e.message}")
            } finally {
                delay(5000)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                Log.d(TAG, "Service fermato (startId=$startId)")
            }
        }

        return START_NOT_STICKY
    }

    // GPS fresco (timeout 10s, poi fallback a last known)
    private suspend fun getPreciseLocation(): Pair<Double, Double>? {
        return suspendCancellableCoroutine { cont ->
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMaxUpdates(1)
                .setMaxUpdateDelayMillis(10_000)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    val loc = result.lastLocation
                    if (loc != null && cont.isActive)
                        cont.resume(Pair(loc.latitude, loc.longitude)) {}
                    else if (cont.isActive)
                        cont.resume(null) {}
                }
            }

            try {
                fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                cont.invokeOnCancellation { fusedLocationClient.removeLocationUpdates(callback) }

                serviceScope.launch {
                    delay(10_000)
                    fusedLocationClient.removeLocationUpdates(callback)
                    if (cont.isActive) {
                        val fallback = getLastKnownLocation()
                        cont.resume(fallback) {}
                    }
                }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null) {}
            }
        }
    }

    private suspend fun getLastKnownLocation(): Pair<Double, Double>? {
        return suspendCancellableCoroutine { cont ->
            try {
                fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    val loc = task.result
                    Log.d(TAG, "lastLocation: success=${task.isSuccessful}, loc=$loc")
                    if (cont.isActive)
                        cont.resume(if (loc != null) Pair(loc.latitude, loc.longitude) else null) {}
                }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null) {}
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}