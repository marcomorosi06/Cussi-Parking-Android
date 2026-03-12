package com.cuscus.cussiparking

import android.app.Application
import com.cuscus.cussiparking.data.SettingsManager
import com.cuscus.cussiparking.local.AppDatabase
import com.cuscus.cussiparking.repository.CussiParkingRepository
import com.cuscus.cussiparking.trigger.ParkingNotificationHelper

class CussiParkingApplication : Application() {

    val settingsManager by lazy { SettingsManager(this) }

    val database by lazy { AppDatabase.getDatabase(this) }

    val repository by lazy {
        CussiParkingRepository(
            settingsManager = settingsManager,
            vehicleDao = database.vehicleDao(),
            triggerDao = database.triggerDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Crea i canali notifica al primo avvio (operazione idempotente su Android 8+)
        ParkingNotificationHelper.createChannels(this)
    }
}