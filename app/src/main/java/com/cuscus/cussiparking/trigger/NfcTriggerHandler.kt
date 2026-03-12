package com.cuscus.cussiparking.trigger

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cuscus.cussiparking.CussiParkingApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NfcTriggerHandler {

    private const val TAG = "NfcTriggerHandler"

    /**
     * Chiama questo da MainActivity.onNewIntent() e onCreate().
     *
     * - payload non CussiParking          → return false (non gestito)
     * - vehicleId trovato nel DB locale   → WorkManager come prima
     * - vehicleId NON trovato nel DB      → UnknownNfcTagState.notify() → bottom sheet
     */
    fun handleIntent(activity: Activity, intent: Intent): Boolean {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TECH_DISCOVERED) return false

        val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMessages == null) {
            Log.w(TAG, "Nessun messaggio NDEF nell'intent (action=${intent.action})")
            return false
        }

        val ndefMessage = rawMessages[0] as? NdefMessage ?: return false
        val payload = parseNdefPayload(ndefMessage.records) ?: run {
            Log.w(TAG, "Payload NFC non riconosciuto come trigger CussiParking")
            return false
        }

        Log.d(TAG, "Tag NFC letto: $payload")

        val vehicleId    = payload["vehicleId"]?.toIntOrNull() ?: run {
            Log.e(TAG, "vehicleId mancante o non intero: $payload")
            return false
        }
        val locationMode = payload["mode"] ?: "last_known"
        val vehicleName  = payload["name"]?.replace("+", " ") ?: "Veicolo"

        val app = activity.applicationContext as CussiParkingApplication
        CoroutineScope(Dispatchers.IO).launch {
            // ── Passo 1: cerca una mappatura NFC già configurata dall'utente ────
            // Il tag può contenere il vehicleId di un altro profilo/dispositivo;
            // tagSourceVehicleId è la chiave di ricerca cross-device.
            val nfcTrigger = app.database.triggerDao().getEnabledNfcTriggerBySource(vehicleId)
            if (nfcTrigger != null) {
                Log.d(TAG, "Trigger NFC trovato → localVehicleId=${nfcTrigger.localVehicleId}, mode=${nfcTrigger.locationMode}")
                val inputData = Data.Builder()
                    .putInt(ParkingLocationWorker.KEY_VEHICLE_ID, nfcTrigger.localVehicleId)
                    .putString(ParkingLocationWorker.KEY_VEHICLE_NAME, nfcTrigger.vehicleName)
                    .putString(ParkingLocationWorker.KEY_LOCATION_MODE, nfcTrigger.locationMode)
                    .putString(ParkingLocationWorker.KEY_TRIGGER_LABEL, "NFC")
                    .build()
                WorkManager.getInstance(activity)
                    .enqueue(OneTimeWorkRequestBuilder<ParkingLocationWorker>().setInputData(inputData).build())
                return@launch
            }

            // ── Passo 2: nessuna mappatura → cerca il veicolo per ID diretto ───
            // Questo ramo vale solo per chi ha scritto il tag (stesso dispositivo,
            // stesso profilo: il vehicleId nel tag coincide con l'ID locale).
            val vehicle = app.database.vehicleDao().getVehicleById(vehicleId)
            if (vehicle != null) {
                Log.d(TAG, "Veicolo trovato per ID diretto → vehicleId=$vehicleId, mode=$locationMode")
                val inputData = Data.Builder()
                    .putInt(ParkingLocationWorker.KEY_VEHICLE_ID, vehicleId)
                    .putString(ParkingLocationWorker.KEY_VEHICLE_NAME, vehicle.name)
                    .putString(ParkingLocationWorker.KEY_LOCATION_MODE, locationMode)
                    .putString(ParkingLocationWorker.KEY_TRIGGER_LABEL, "NFC")
                    .build()
                WorkManager.getInstance(activity)
                    .enqueue(OneTimeWorkRequestBuilder<ParkingLocationWorker>().setInputData(inputData).build())
                return@launch
            }

            // ── Passo 3: tag completamente sconosciuto → chiedi configurazione ──
            Log.w(TAG, "vehicleId=$vehicleId non nel DB e nessun trigger NFC mappato → bottom sheet")
            UnknownNfcTagState.notify(
                UnknownNfcTagData(
                    locationMode   = locationMode,
                    tagVehicleName = vehicleName,
                    tagVehicleId   = vehicleId
                )
            )
        }

        return true
    }

    private fun parseNdefPayload(records: Array<NdefRecord>): Map<String, String>? {
        for (record in records) {
            when {
                record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(NdefRecord.RTD_URI) -> {
                    val uri = decodeUriPayload(record.payload)
                    Log.d(TAG, "URI record: $uri")
                    if (uri != null && uri.startsWith("cussiparking://trigger"))
                        return parseQueryString(uri.substringAfter("?", ""))
                }
                record.tnf == NdefRecord.TNF_ABSOLUTE_URI -> {
                    val uri = String(record.type, Charsets.UTF_8)
                    Log.d(TAG, "Absolute URI: $uri")
                    if (uri.startsWith("cussiparking://trigger"))
                        return parseQueryString(uri.substringAfter("?", ""))
                }
                record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                    val text = decodeTextPayload(record.payload)
                    Log.d(TAG, "Text record: $text")
                    if (text != null && text.startsWith("cussiparking:trigger:")) {
                        return text.removePrefix("cussiparking:trigger:")
                            .split(":")
                            .mapNotNull { kv -> kv.split("=").takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                            .toMap()
                    }
                }
                else -> Log.v(TAG, "Record ignorato tnf=${record.tnf}")
            }
        }
        return null
    }

    private fun decodeUriPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val prefix = when (payload[0].toInt() and 0xFF) {
            0x00 -> ""; 0x01 -> "http://www."; 0x02 -> "https://www."
            0x03 -> "http://"; 0x04 -> "https://"; 0x05 -> "tel:"; 0x06 -> "mailto:"
            else -> ""
        }
        return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }

    private fun decodeTextPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val offset = 1 + (status and 0x3F)
        if (offset >= payload.size) return null
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        return String(payload, offset, payload.size - offset, charset)
    }

    private fun parseQueryString(query: String): Map<String, String>? {
        if (query.isBlank()) return null
        return query.split("&")
            .mapNotNull { it.split("=").takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }
            .toMap()
            .ifEmpty { null }
    }
}