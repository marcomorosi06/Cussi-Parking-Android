package com.cuscus.cussiparking.repository

import com.cuscus.cussiparking.data.SettingsManager
import com.cuscus.cussiparking.local.TriggerDao
import com.cuscus.cussiparking.local.VehicleDao
import com.cuscus.cussiparking.local.VehicleEntity
import com.cuscus.cussiparking.network.*

data class LocationsResult(val vehicles: List<Vehicle>, val showBanner: Boolean, val unreachableProfiles: List<String> = emptyList())

class CussiParkingRepository(
    private val settingsManager: SettingsManager,
    private val vehicleDao: VehicleDao,
    private val triggerDao: TriggerDao
) {

    private fun getApi(serverUrl: String): CussiParkingApi? =
        if (serverUrl.isBlank()) null else NetworkClient.getApi(serverUrl)

    // ==========================================
    // AUTH — operazioni su un profilo specifico
    // ==========================================

    suspend fun login(profileId: String, email: String, password: String): Result<LoginResponse> {
        val profile = settingsManager.getProfile(profileId)
            ?: return Result.failure(Exception("Profilo non trovato."))
        return try {
            val api = getApi(profile.serverUrl) ?: throw Exception("URL non configurato.")
            val response = api.login(email, password)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success" && !body.token.isNullOrBlank()) {
                settingsManager.saveProfileToken(profileId, body.token, body.userId ?: -1)
                Result.success(body)
            } else throw Exception(body?.message ?: "Errore login")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Errore di rete"))
        }
    }

    suspend fun register(profileId: String, email: String, password: String, username: String): Result<Boolean> {
        val profile = settingsManager.getProfile(profileId)
            ?: return Result.failure(Exception("Profilo non trovato."))
        return try {
            val api = getApi(profile.serverUrl) ?: throw Exception("URL non configurato.")
            val response = api.register(email, password, username)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success") Result.success(true)
            else throw Exception(body?.message ?: "Errore registrazione")
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Errore di rete"))
        }
    }

    /**
     * Logout da un profilo: rimuove token ma mantiene profilo.
     * Cancella tutti i veicoli sincronizzati da quel server (syncState=1)
     * e i relativi trigger — i veicoli locali (syncState=0) restano.
     */
    suspend fun logoutProfile(profileId: String) {
        settingsManager.logoutProfile(profileId)
        cleanupVehiclesForProfile(profileId, deleteLocalOnly = false)
    }

    /**
     * Rimozione completa del profilo: cancella tutto, inclusi i veicoli locali.
     */
    suspend fun removeProfile(profileId: String) {
        cleanupVehiclesForProfile(profileId, deleteLocalOnly = true)
        settingsManager.removeProfile(profileId)
    }

    /**
     * Cancella i veicoli associati a un profilo e i loro trigger.
     * Se deleteLocalOnly=false cancella solo i sincronizzati (syncState != 0).
     * Se deleteLocalOnly=true cancella tutti.
     */
    private suspend fun cleanupVehiclesForProfile(profileId: String, deleteLocalOnly: Boolean) {
        val vehicles = vehicleDao.getAllByProfile(profileId)
        vehicles
            .filter { deleteLocalOnly || it.syncState != 0 }
            .forEach { vehicle ->
                // Cancella i trigger associati a questo veicolo
                triggerDao.deleteTriggersForVehicle(vehicle.id)
                // Cancella il veicolo
                vehicleDao.deleteVehicle(vehicle.id)
            }
    }

    suspend fun deleteAccount(profileId: String, password: String): Result<String> {
        return try {
            val (api, token) = getApiAndToken(profileId) ?: throw Exception("Non autenticato.")
            val response = api.deleteAccount(token, password)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success") {
                // Rimuovi profilo + tutti i veicoli e trigger associati
                removeProfile(profileId)
                Result.success(body.message ?: "Account eliminato.")
            } else throw Exception(body?.message ?: "Errore")
        } catch (e: Exception) { Result.failure(Exception(e.message ?: "Errore di rete")) }
    }

    // ==========================================
    // VEICOLI
    // ==========================================

    suspend fun addVehicle(name: String, icon: String, forceLocalOnly: Boolean = false, profileId: String? = null): Result<Boolean> {
        if (forceLocalOnly || profileId == null) {
            vehicleDao.insertVehicle(VehicleEntity(name = name, icon = icon, syncState = 0, serverProfileId = null, serverLabel = null))
            return Result.success(true)
        }
        val profile = settingsManager.getProfile(profileId) ?: run {
            vehicleDao.insertVehicle(VehicleEntity(name = name, icon = icon, syncState = 0))
            return Result.success(true)
        }
        try {
            val api = getApi(profile.serverUrl)
            val token = profile.token
            if (api != null && !token.isNullOrBlank()) {
                val response = api.addVehicle(token, name, icon)
                if (response.isSuccessful && response.body()?.status == "success") return Result.success(true)
            }
        } catch (e: Exception) { /* fallthrough */ }
        vehicleDao.insertVehicle(VehicleEntity(name = name, icon = icon, syncState = 0, serverProfileId = profileId, serverLabel = profile.label))
        return Result.success(true)
    }

    suspend fun updateLocation(localId: Int, lat: Double, lng: Double): Result<Boolean> {
        val timestamp = System.currentTimeMillis() / 1000
        val vehicle = vehicleDao.getVehicleById(localId) ?: return Result.failure(Exception("Veicolo non trovato"))
        val isOffline = settingsManager.isOfflineMode.value

        val profile = vehicle.serverProfileId?.let { settingsManager.getProfile(it) }

        if (isOffline || profile == null || vehicle.serverId == null) {
            val newState = if (vehicle.serverId != null) 2 else 0
            vehicleDao.updateVehicle(localId, lat, lng, timestamp, newState)
            return Result.success(true)
        }
        return try {
            val response = getApi(profile.serverUrl)!!.updateLocation(profile.token!!, vehicle.serverId, lat, lng, timestamp)
            if (response.isSuccessful && response.body()?.status == "success") {
                vehicleDao.updateVehicle(localId, lat, lng, timestamp, 1)
                Result.success(true)
            } else throw Exception("Errore server")
        } catch (e: Exception) {
            vehicleDao.updateVehicle(localId, lat, lng, timestamp, 2)
            Result.success(true)
        }
    }

    suspend fun getLocations(): LocationsResult {
        val isOffline = settingsManager.isOfflineMode.value
        val unreachableLabels = mutableListOf<String>()

        if (!isOffline) {
            val loggedProfiles = settingsManager.profiles.value.filter { !it.token.isNullOrBlank() }

            for (profile in loggedProfiles) {
                try {
                    val api = getApi(profile.serverUrl) ?: continue
                    val response = api.getLocations(profile.token!!)
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val serverVehicles = response.body()?.data ?: emptyList()

                        vehicleDao.getPendingUpdatesByProfile(profile.id).forEach { p ->
                            try {
                                api.updateLocation(profile.token, p.serverId!!, p.lat!!, p.lng!!, p.updatedAt!!)
                                vehicleDao.updateVehicleSyncState(p.id, 1)
                            } catch (e: Exception) { /* ignora */ }
                        }

                        val serverIds = serverVehicles.map { it.id }.toSet()

                        serverVehicles.forEach { sv ->
                            val local = vehicleDao.getVehicleByServerIdAndProfile(sv.id, profile.id)
                            if (local == null) {
                                vehicleDao.insertVehicle(VehicleEntity(
                                    serverId = sv.id, name = sv.name, icon = sv.icon ?: "",
                                    lat = sv.lat, lng = sv.lng, updatedAt = sv.updatedAt,
                                    syncState = 1, serverProfileId = profile.id, serverLabel = profile.label,
                                    role = sv.role ?: "owner", lastUpdatedBy = sv.lastUpdatedBy
                                ))
                            } else if (local.syncState != 2) {
                                vehicleDao.updateFullVehicle(
                                    id = local.id,
                                    serverId = sv.id,
                                    name = sv.name,
                                    icon = sv.icon ?: "",
                                    lat = sv.lat,
                                    lng = sv.lng,
                                    updatedAt = sv.updatedAt,
                                    syncState = 1,
                                    role = sv.role ?: "owner",
                                    lastUpdatedBy = sv.lastUpdatedBy
                                )
                            }
                        }

                        // Rimuovi veicoli non più presenti sul server (inclusi quelli da cui sei stato rimosso come membro)
                        vehicleDao.getAllByProfile(profile.id)
                            .filter { it.syncState == 1 && it.serverId != null && it.serverId !in serverIds }
                            .forEach { vehicle ->
                                triggerDao.deleteTriggersForVehicle(vehicle.id)
                                vehicleDao.deleteVehicle(vehicle.id)
                            }

                    } else unreachableLabels.add(profile.label)
                } catch (e: Exception) { unreachableLabels.add(profile.label) }
            }
        }

        val allVehicles = vehicleDao.getAllVehicles().map { it.toNetworkModel() }
        val showBanner = !isOffline && unreachableLabels.isNotEmpty()
        return LocationsResult(allVehicles, showBanner, unreachableLabels)
    }

    suspend fun deleteVehicle(localId: Int) {
        val vehicle = vehicleDao.getVehicleById(localId) ?: return
        // Cancella i trigger associati prima del veicolo
        triggerDao.deleteTriggersForVehicle(localId)
        vehicleDao.deleteVehicle(localId)
        if (vehicle.serverId != null && vehicle.serverProfileId != null) {
            try {
                val profile = settingsManager.getProfile(vehicle.serverProfileId) ?: return
                val token = profile.token ?: return
                getApi(profile.serverUrl)?.deleteVehicle(token, vehicle.serverId)
            } catch (e: Exception) { /* offline: ignora */ }
        }
    }

    // ==========================================
    // GESTIONE MEMBRI
    // ==========================================

    private fun getApiAndToken(profileId: String): Pair<CussiParkingApi, String>? {
        val profile = settingsManager.getProfile(profileId) ?: return null
        val token = profile.token ?: return null
        val api = getApi(profile.serverUrl) ?: return null
        return Pair(api, token)
    }

    suspend fun getMembers(vehicleServerId: Int, profileId: String): Result<List<Member>> {
        return try {
            val (api, token) = getApiAndToken(profileId) ?: throw Exception("Non autenticato.")
            val response = api.getMembers(token, vehicleServerId)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success") Result.success(body.data ?: emptyList())
            else throw Exception(body?.message ?: "Errore")
        } catch (e: Exception) { Result.failure(Exception(e.message ?: "Errore di rete")) }
    }

    suspend fun addMember(vehicleServerId: Int, username: String, profileId: String): Result<String> {
        return try {
            val (api, token) = getApiAndToken(profileId) ?: throw Exception("Non autenticato.")
            val response = api.addMember(token, vehicleServerId, username)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success") Result.success(body.message ?: "Membro aggiunto.")
            else throw Exception(body?.message ?: "Errore")
        } catch (e: Exception) { Result.failure(Exception(e.message ?: "Errore di rete")) }
    }

    suspend fun removeMember(vehicleServerId: Int, targetUserId: Int, profileId: String): Result<String> {
        return try {
            val (api, token) = getApiAndToken(profileId) ?: throw Exception("Non autenticato.")
            val response = api.removeMember(token, vehicleServerId, targetUserId)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success") Result.success(body.message ?: "Rimosso.")
            else throw Exception(body?.message ?: "Errore")
        } catch (e: Exception) { Result.failure(Exception(e.message ?: "Errore di rete")) }
    }

    suspend fun changeRole(vehicleServerId: Int, targetUserId: Int, newRole: String, profileId: String): Result<String> {
        return try {
            val (api, token) = getApiAndToken(profileId) ?: throw Exception("Non autenticato.")
            val response = api.changeRole(token, vehicleServerId, targetUserId, newRole)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success") Result.success(body.message ?: "Ruolo aggiornato.")
            else throw Exception(body?.message ?: "Errore")
        } catch (e: Exception) { Result.failure(Exception(e.message ?: "Errore di rete")) }
    }

    suspend fun generateInviteCode(vehicleServerId: Int, profileId: String): Result<InviteCodeResponse> {
        return try {
            val (api, token) = getApiAndToken(profileId) ?: throw Exception("Non autenticato.")
            val response = api.inviteCode(token, "generate", vehicleId = vehicleServerId)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success") Result.success(body)
            else throw Exception(body?.message ?: "Errore generazione codice")
        } catch (e: Exception) { Result.failure(Exception(e.message ?: "Errore di rete")) }
    }

    suspend fun joinWithInviteCode(code: String, profileId: String): Result<InviteCodeResponse> {
        return try {
            val (api, token) = getApiAndToken(profileId) ?: throw Exception("Non autenticato.")
            val response = api.inviteCode(token, "join", code = code)
            val body = response.body()
            if (response.isSuccessful && body?.status == "success") Result.success(body!!)
            else throw Exception(body?.message ?: "Codice non valido")
        } catch (e: Exception) { Result.failure(Exception(e.message ?: "Errore di rete")) }
    }
}