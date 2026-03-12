package com.cuscus.cussiparking.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un trigger automatico per il parcheggio.
 * Quando la condizione si avvera (es. disconnessione WiFi/BT),
 * l'app salva automaticamente la posizione del veicolo associato.
 */
@Entity(tableName = "triggers")
data class TriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // A quale veicolo locale appartiene questo trigger
    val localVehicleId: Int,

    // Nome leggibile del veicolo (cache per notifiche senza query DB)
    val vehicleName: String,

    // TIPO: "wifi" o "bluetooth"
    val type: String,

    // WiFi: SSID della rete (es. "Casa di Marco")
    // Bluetooth: indirizzo MAC del dispositivo (es. "AA:BB:CC:DD:EE:FF")
    // NFC: "nfc" (costante fissa — la chiave di ricerca è tagSourceVehicleId)
    val identifier: String,

    // Etichetta leggibile: nome rete WiFi / nome dispositivo BT / nome tag NFC
    val label: String,

    // Solo per trigger NFC: vehicleId scritto fisicamente nel tag (può essere
    // l'ID di un altro utente/profilo). Ci permette di trovare la mappatura
    // tag→veicolo locale senza dipendere dall'ID scritto nel tag.
    // Null per trigger WiFi e Bluetooth.
    val tagSourceVehicleId: Int? = null,

    // MODALITÀ GPS: "precise" = GPS fresco, "last_known" = ultima posizione nota
    val locationMode: String = "last_known",

    // Trigger attivo o sospeso temporaneamente
    val enabled: Boolean = true
)