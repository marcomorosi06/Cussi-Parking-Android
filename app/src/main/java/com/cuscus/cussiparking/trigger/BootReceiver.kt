package com.cuscus.cussiparking.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Viene chiamato al riavvio del dispositivo.
 * I BroadcastReceiver dichiarati nel Manifest sono già registrati automaticamente
 * da Android — questo receiver serve principalmente come hook per future
 * operazioni di inizializzazione post-boot (es. re-schedulare WorkManager tasks).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("CussiParking", "Boot completed — trigger receivers attivi")
            // I receiver statici nel Manifest si attivano già automaticamente.
            // Qui potremmo in futuro schedulare WorkManager per sync periodica.
        }
    }
}