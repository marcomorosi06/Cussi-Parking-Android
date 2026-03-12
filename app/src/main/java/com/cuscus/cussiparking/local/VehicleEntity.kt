package com.cuscus.cussiparking.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Int? = null,
    val name: String,
    val icon: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val updatedAt: Long? = null,
    val syncState: Int = 0,
    val serverProfileId: String? = null,
    val serverLabel: String? = null,
    val role: String = "owner",
    val lastUpdatedBy: String? = null
) {
    fun toNetworkModel() = com.cuscus.cussiparking.network.Vehicle(
        id = this.id, serverId = this.serverId, name = this.name, icon = this.icon,
        lat = this.lat, lng = this.lng, updatedAt = this.updatedAt,
        role = this.role, syncState = this.syncState,
        serverProfileId = this.serverProfileId, serverLabel = this.serverLabel,
        lastUpdatedBy = this.lastUpdatedBy
    )
}