package com.cuscus.cussiparking.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerDao {

    @Query("SELECT * FROM triggers ORDER BY localVehicleId, type")
    fun getAllTriggersFlow(): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM triggers WHERE localVehicleId = :vehicleId")
    fun getTriggersForVehicleFlow(vehicleId: Int): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM triggers WHERE type = :type AND enabled = 1")
    suspend fun getEnabledTriggersByType(type: String): List<TriggerEntity>

    @Query("SELECT * FROM triggers WHERE type = 'wifi' AND identifier = :ssid AND enabled = 1")
    suspend fun getEnabledWifiTriggers(ssid: String): List<TriggerEntity>

    @Query("SELECT * FROM triggers WHERE type = 'bluetooth' AND identifier = :mac AND enabled = 1")
    suspend fun getEnabledBluetoothTriggers(mac: String): List<TriggerEntity>

    /**
     * Cerca un trigger NFC mappato al vehicleId scritto nel tag fisico.
     * Restituisce il trigger anche se il vehicleId nel tag appartiene a un
     * altro utente/profilo (cross-device mapping).
     */
    @Query("SELECT * FROM triggers WHERE type = 'nfc' AND tagSourceVehicleId = :tagVehicleId AND enabled = 1 LIMIT 1")
    suspend fun getEnabledNfcTriggerBySource(tagVehicleId: Int): TriggerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrigger(trigger: TriggerEntity): Long

    @Update
    suspend fun updateTrigger(trigger: TriggerEntity)

    @Query("DELETE FROM triggers WHERE id = :id")
    suspend fun deleteTrigger(id: Int)

    @Query("DELETE FROM triggers WHERE localVehicleId = :vehicleId")
    suspend fun deleteTriggersForVehicle(vehicleId: Int)

    @Query("UPDATE triggers SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}