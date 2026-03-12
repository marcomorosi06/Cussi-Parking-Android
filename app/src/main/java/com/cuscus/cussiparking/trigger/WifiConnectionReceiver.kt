package com.cuscus.cussiparking.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager

/**
 * Salva l'SSID corrente ogni volta che ci connettiamo a una rete WiFi.
 * Questo ci permette di sapere da quale rete ci siamo appena disconnessi
 * quando WifiTriggerReceiver rileva la disconnessione.
 */
class WifiConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return

        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return

        val info = wifiManager.connectionInfo ?: return
        val ssid = info.ssid?.trim('"') ?: return
        if (ssid.isEmpty() || ssid == "<unknown ssid>") return

        // Salva l'SSID corrente — sarà usato da WifiTriggerReceiver alla disconnessione
        context.getSharedPreferences("wifi_trigger_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_connected_ssid", ssid)
            .apply()
    }
}