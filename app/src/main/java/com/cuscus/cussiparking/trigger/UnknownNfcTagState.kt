package com.cuscus.cussiparking.trigger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UnknownNfcTagData(
    val locationMode:   String,
    val tagVehicleName: String,
    // vehicleId scritto fisicamente nel tag (ID del profilo che ha scritto il tag)
    val tagVehicleId:   Int
)

/**
 * Canale one-shot tra NfcTriggerHandler (producer) e il bottom sheet in MainActivity (consumer).
 *
 * Regola: notify() è idempotente — se il pending è già non-null non fa nulla.
 * Così avvicinare di nuovo il tag mentre il bottom sheet è aperto non lo riapre.
 * consume() va chiamato solo quando il bottom sheet ha completato la sua chiusura.
 */
object UnknownNfcTagState {

    private val _pending = MutableStateFlow<UnknownNfcTagData?>(null)
    val pending: StateFlow<UnknownNfcTagData?> = _pending.asStateFlow()

    internal fun notify(data: UnknownNfcTagData) {
        // Se c'è già un pending attivo (sheet aperto) ignoriamo: idempotente.
        if (_pending.value == null) {
            _pending.value = data
        }
    }

    fun consume() {
        _pending.value = null
    }
}