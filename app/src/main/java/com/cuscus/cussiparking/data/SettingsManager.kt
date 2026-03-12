package com.cuscus.cussiparking.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

// Un profilo = un server + un account loggato su quel server
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(), // ID locale univoco
    val label: String,        // Nome amichevole es. "Casa" o "Lavoro"
    val serverUrl: String,
    val email: String,
    val token: String? = null,    // null = non ancora loggato
    val userId: Int? = null
)

class SettingsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, "secure_app_settings_v2", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()

    companion object {
        private const val KEY_IS_OFFLINE   = "is_offline_mode"
        private const val KEY_MAP_BEHAVIOR = "map_behavior"
        private const val KEY_CUSTOM_LAT   = "custom_lat"
        private const val KEY_CUSTOM_LNG   = "custom_lng"
        private const val KEY_PROFILES     = "server_profiles"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
    }

    // --- MODALITA' OFFLINE ---
    private val _isOfflineMode = MutableStateFlow(prefs.getBoolean(KEY_IS_OFFLINE, true))
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    fun setOfflineMode(isOffline: Boolean) {
        prefs.edit().putBoolean(KEY_IS_OFFLINE, isOffline).apply()
        _isOfflineMode.value = isOffline
    }

    // --- MAPPA ---
    private val _mapBehavior = MutableStateFlow(prefs.getString(KEY_MAP_BEHAVIOR, "gps") ?: "gps")
    val mapBehavior: StateFlow<String> = _mapBehavior.asStateFlow()

    private val _customLat = MutableStateFlow(prefs.getFloat(KEY_CUSTOM_LAT, 41.8902f).toDouble())
    val customLat: StateFlow<Double> = _customLat.asStateFlow()

    private val _customLng = MutableStateFlow(prefs.getFloat(KEY_CUSTOM_LNG, 12.4922f).toDouble())
    val customLng: StateFlow<Double> = _customLng.asStateFlow()

    fun saveMapBehavior(behavior: String) {
        prefs.edit().putString(KEY_MAP_BEHAVIOR, behavior).apply()
        _mapBehavior.value = behavior
    }

    fun saveCustomLocation(lat: Double, lng: Double) {
        prefs.edit().putFloat(KEY_CUSTOM_LAT, lat.toFloat()).putFloat(KEY_CUSTOM_LNG, lng.toFloat()).apply()
        _customLat.value = lat; _customLng.value = lng
    }

    // --- PROFILI SERVER (multi-server) ---
    private fun loadProfiles(): List<ServerProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ServerProfile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun saveProfiles(profiles: List<ServerProfile>) {
        prefs.edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply()
        _profiles.value = profiles
    }

    private val _profiles = MutableStateFlow(loadProfiles())
    val profiles: StateFlow<List<ServerProfile>> = _profiles.asStateFlow()

    // Aggiunge un profilo nuovo (senza token — viene loggato dopo)
    fun addProfile(label: String, serverUrl: String, email: String): ServerProfile {
        val profile = ServerProfile(label = label, serverUrl = serverUrl.trimEnd('/'), email = email)
        saveProfiles(_profiles.value + profile)
        return profile
    }

    // Salva il token dopo il login riuscito
    fun saveProfileToken(profileId: String, token: String, userId: Int) {
        val updated = _profiles.value.map {
            if (it.id == profileId) it.copy(token = token, userId = userId) else it
        }
        saveProfiles(updated)
    }

    // Aggiorna il label di un profilo
    fun updateProfileLabel(profileId: String, newLabel: String) {
        val updated = _profiles.value.map {
            if (it.id == profileId) it.copy(label = newLabel) else it
        }
        saveProfiles(updated)
    }

    // Rimuove un profilo (logout + cancella)
    fun removeProfile(profileId: String) {
        saveProfiles(_profiles.value.filter { it.id != profileId })
    }

    // Logout da un profilo (mantiene URL e email, cancella solo il token)
    fun logoutProfile(profileId: String) {
        val updated = _profiles.value.map {
            if (it.id == profileId) it.copy(token = null, userId = null) else it
        }
        saveProfiles(updated)
    }

    fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingCompleted(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun getProfile(profileId: String): ServerProfile? = _profiles.value.find { it.id == profileId }

    // Compatibilità con codice legacy che usa serverUrl/authToken singoli
    // Usa il primo profilo loggato come "profilo attivo"
    val serverUrl: StateFlow<String> get() = MutableStateFlow(
        _profiles.value.firstOrNull { it.token != null }?.serverUrl ?: ""
    )
    val authToken: StateFlow<String?> get() = MutableStateFlow(
        _profiles.value.firstOrNull { it.token != null }?.token
    )
    val userId: StateFlow<Int> get() = MutableStateFlow(
        _profiles.value.firstOrNull { it.token != null }?.userId ?: -1
    )

    fun clearAuth() {
        // Legacy: fa logout da tutti i profili
        saveProfiles(_profiles.value.map { it.copy(token = null, userId = null) })
    }

    // Manteniamo questi per compatibilità con SettingsScreen vecchio
    fun saveServerUrl(url: String) { /* no-op nel multi-server, usa addProfile */ }
    fun saveAuthToken(token: String) { /* no-op */ }
    fun saveUserId(id: Int) { /* no-op */ }
}