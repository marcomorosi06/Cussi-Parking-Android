package com.cuscus.cussiparking.trigger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cuscus.cussiparking.MainActivity
import com.cuscus.cussiparking.R

/**
 * Gestione centralizzata delle notifiche di parcheggio.
 *
 * Canali:
 *  - CHANNEL_TRIGGER  : notifiche di parcheggio automatico (BT, NFC, WiFi) — importanza DEFAULT
 *  - CHANNEL_MANUAL   : parcheggio manuale da mappa/GPS — importanza LOW (silenziosa)
 *  - CHANNEL_FOREGROUND: notifica ongoing per il ForegroundService — importanza MIN
 *
 * Ogni sorgente ha icona, titolo e testo diversi.
 */
object ParkingNotificationHelper {

    const val CHANNEL_TRIGGER    = "cussiparking_trigger_result"
    const val CHANNEL_MANUAL     = "cussiparking_manual"
    const val CHANNEL_FOREGROUND = "cussiparking_fg"

    // ID base per le notifiche di risultato; offset per tipo trigger
    private const val NOTIF_BASE_BT    = 2000
    private const val NOTIF_BASE_NFC   = 3000
    private const val NOTIF_BASE_WIFI  = 4000
    private const val NOTIF_BASE_GPS   = 5000
    private const val NOTIF_BASE_MANUAL= 6000

    // Sorgente del salvataggio — passata come stringa in WorkManager / Service
    const val SOURCE_BLUETOOTH = "bluetooth"
    const val SOURCE_NFC       = "nfc"
    const val SOURCE_WIFI      = "wifi"
    const val SOURCE_GPS       = "gps"       // GPS manuale da schermata
    const val SOURCE_MANUAL    = "manual"    // tap su mappa

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // Canale trigger automatici: suono e vibrazione abilitati
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRIGGER,
                context.getString(R.string.channel_trigger_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_trigger_desc)
                enableVibration(true)
            }
        )

        // Canale parcheggio manuale: silenziosa, solo nella shade
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MANUAL,
                context.getString(R.string.channel_manual_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_manual_desc)
            }
        )

        // Canale foreground service: minimale, non distrae
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                context.getString(R.string.channel_fg_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.channel_fg_desc)
                setShowBadge(false)
            }
        )
    }

    /**
     * Notifica di **successo** dopo il salvataggio della posizione.
     *
     * @param vehicleId  usato per calcolare un notificationId stabile per veicolo
     * @param source     una delle costanti SOURCE_* sopra
     * @param triggerLabel  nome del dispositivo BT / SSID WiFi / "NFC" ecc.
     */
    fun notifySuccess(
        context:      Context,
        vehicleId:    Int,
        vehicleName:  String,
        source:       String,
        triggerLabel: String
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val (channel, notifId, icon, title, body) = when (source) {

            SOURCE_BLUETOOTH -> NotifConfig(
                channel  = CHANNEL_TRIGGER,
                id       = NOTIF_BASE_BT + vehicleId,
                icon     = android.R.drawable.stat_sys_data_bluetooth,
                title    = context.getString(R.string.notif_title_parked, vehicleName),
                body     = context.getString(R.string.notif_body_bt, triggerLabel)
            )

            SOURCE_NFC -> NotifConfig(
                channel  = CHANNEL_TRIGGER,
                id       = NOTIF_BASE_NFC + vehicleId,
                icon     = R.drawable.ic_launcher_foreground,
                title    = context.getString(R.string.notif_title_parked, vehicleName),
                body     = context.getString(R.string.notif_body_nfc)
            )

            SOURCE_WIFI -> NotifConfig(
                channel  = CHANNEL_TRIGGER,
                id       = NOTIF_BASE_WIFI + vehicleId,
                icon     = R.drawable.ic_launcher_foreground,
                title    = context.getString(R.string.notif_title_parked, vehicleName),
                body     = context.getString(R.string.notif_body_wifi, triggerLabel)
            )

            SOURCE_GPS -> NotifConfig(
                channel  = CHANNEL_MANUAL,
                id       = NOTIF_BASE_GPS + vehicleId,
                icon     = android.R.drawable.ic_menu_mylocation,
                title    = context.getString(R.string.notif_title_saved, vehicleName),
                body     = context.getString(R.string.notif_body_gps)
            )

            SOURCE_MANUAL -> NotifConfig(
                channel  = CHANNEL_MANUAL,
                id       = NOTIF_BASE_MANUAL + vehicleId,
                icon     = android.R.drawable.ic_menu_mapmode,
                title    = context.getString(R.string.notif_title_saved, vehicleName),
                body     = context.getString(R.string.notif_body_manual)
            )

            else -> NotifConfig(
                channel  = CHANNEL_TRIGGER,
                id       = NOTIF_BASE_BT + vehicleId,
                icon     = R.drawable.ic_launcher_foreground,
                title    = context.getString(R.string.notif_title_parked, vehicleName),
                body     = context.getString(R.string.notif_body_auto)
            )
        }

        // Intent per aprire l'app al tap sulla notifica
        val tapIntent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(
                if (channel == CHANNEL_MANUAL) NotificationCompat.PRIORITY_LOW
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()

        nm.notify(notifId, notification)
    }

    /**
     * Notifica di **errore** (posizione non disponibile).
     */
    fun notifyFailure(
        context:     Context,
        vehicleId:   Int,
        vehicleName: String,
        reason:      String
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val notifId = 9000 + vehicleId

        val notification = NotificationCompat.Builder(context, CHANNEL_TRIGGER)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notif_title_error, vehicleName))
            .setContentText(reason)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(notifId, notification)
    }

    /**
     * Notifica ongoing per il ForegroundService (richiesta obbligatoria Android 8+).
     */
    fun buildForegroundNotification(context: Context, text: String) =
        NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("CussiParking")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()

    // ── Data class helper interno ──────────────────────────────────────────────
    private data class NotifConfig(
        val channel: String,
        val id:      Int,
        val icon:    Int,
        val title:   String,
        val body:    String
    )
}