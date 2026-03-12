package com.cuscus.cussiparking.trigger

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.net.URLEncoder

object NfcWriteHelper {

    /**
     * Scrive sul tag fisico il record URI per questo veicolo.
     * Restituisce null se ok, altrimenti il messaggio di errore.
     *
     * URI scritto: cussiparking://trigger?vehicleId=X&mode=Y&name=Z
     */
    fun writeTag(tag: Tag, vehicleId: Int, vehicleName: String, locationMode: String): String? {
        return try {
            val encodedName = URLEncoder.encode(vehicleName, "UTF-8")
            val uri = "cussiparking://trigger?vehicleId=$vehicleId&mode=$locationMode&name=$encodedName"
            val record  = NdefRecord.createUri(uri)
            val message = NdefMessage(arrayOf(record))

            // Caso 1: tag già formattato NDEF
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return "Il tag è in sola lettura"
                }
                if (ndef.maxSize < message.byteArrayLength) {
                    ndef.close()
                    return "Tag troppo piccolo (${ndef.maxSize} byte disponibili, ${message.byteArrayLength} necessari)"
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                return null // successo
            }

            // Caso 2: tag non ancora formattato (es. tag nuovo NTAG213)
            val formatable = NdefFormatable.get(tag)
            if (formatable != null) {
                formatable.connect()
                formatable.format(message)
                formatable.close()
                return null // successo
            }

            "Il tag non supporta NDEF"
        } catch (e: Exception) {
            "Errore scrittura: ${e.message}"
        }
    }
}