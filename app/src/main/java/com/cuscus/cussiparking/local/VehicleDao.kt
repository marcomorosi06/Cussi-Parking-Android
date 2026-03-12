package com.cuscus.cussiparking.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VehicleDao {

    @Query("SELECT * FROM local_vehicles")
    suspend fun getAllVehicles(): List<VehicleEntity>

    @Query("SELECT * FROM local_vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: Int): VehicleEntity?

    @Query("SELECT * FROM local_vehicles WHERE serverId = :serverId LIMIT 1")
    suspend fun getVehicleByServerId(serverId: Int): VehicleEntity?

    // Multi-server: cerca per serverId + profilo
    @Query("SELECT * FROM local_vehicles WHERE serverId = :serverId AND serverProfileId = :profileId LIMIT 1")
    suspend fun getVehicleByServerIdAndProfile(serverId: Int, profileId: String): VehicleEntity?

    // Tutti i veicoli di un profilo (per pulizia veicoli rimossi dal server)
    @Query("SELECT * FROM local_vehicles WHERE serverProfileId = :profileId")
    suspend fun getAllByProfile(profileId: String): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Query("UPDATE local_vehicles SET lat = :lat, lng = :lng, updatedAt = :timestamp, syncState = :syncState, lastUpdatedBy = :lastUpdatedBy WHERE id = :id")
    suspend fun updateVehicle(id: Int, lat: Double, lng: Double, timestamp: Long, syncState: Int, lastUpdatedBy: String? = null)

    // Aggiorna tutti i campi del veicolo preservando l'id locale (evita che l'autoincrement cambi)
    @Query("""UPDATE local_vehicles 
        SET serverId = :serverId, name = :name, icon = :icon, lat = :lat, lng = :lng,
            updatedAt = :updatedAt, syncState = :syncState, role = :role, lastUpdatedBy = :lastUpdatedBy
        WHERE id = :id""")
    suspend fun updateFullVehicle(
        id: Int, serverId: Int?, name: String, icon: String,
        lat: Double?, lng: Double?, updatedAt: Long?,
        syncState: Int, role: String, lastUpdatedBy: String?
    )

    @Query("UPDATE local_vehicles SET syncState = :state WHERE id = :id")
    suspend fun updateVehicleSyncState(id: Int, state: Int)

    @Query("UPDATE local_vehicles SET role = :role WHERE id = :id")
    suspend fun updateVehicleRole(id: Int, role: String)

    // Aggiornamenti gialli pendenti per un profilo specifico
    @Query("SELECT * FROM local_vehicles WHERE syncState = 2 AND serverId IS NOT NULL AND serverProfileId = :profileId")
    suspend fun getPendingUpdatesByProfile(profileId: String): List<VehicleEntity>

    // NON più usata nella sync — mantenuta solo per eventuale uso futuro
    @Query("DELETE FROM local_vehicles WHERE serverProfileId = :profileId AND syncState = 1")
    suspend fun deleteSyncedByProfile(profileId: String)

    @Query("DELETE FROM local_vehicles WHERE id = :localId")
    suspend fun deleteVehicle(localId: Int)

    // Elimina tutti i veicoli di un profilo (quando si rimuove il profilo)
    @Query("DELETE FROM local_vehicles WHERE serverProfileId = :profileId")
    suspend fun deleteAllByProfile(profileId: String)
}