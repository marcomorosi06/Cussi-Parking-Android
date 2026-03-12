package com.cuscus.cussiparking.network

import com.google.gson.annotations.SerializedName

data class BaseResponse(val status: String, val message: String? = null)

data class LoginResponse(
    val status: String, val message: String? = null,
    val token: String? = null,
    @SerializedName("user_id") val userId: Int? = null
)

data class AddVehicleResponse(
    val status: String, val message: String? = null,
    @SerializedName("vehicle_id") val vehicleId: Int? = null
)

data class Vehicle(
    val id: Int,
    var serverId: Int? = null,
    val name: String,
    val icon: String?,
    val lat: Double?,
    val lng: Double?,
    @SerializedName("updated_at") val updatedAt: Long?,
    val role: String?,
    var syncState: Int = 1,
    @SerializedName("last_updated_by") val lastUpdatedBy: String? = null,
    // Aggiunto lato client, non viene dal JSON
    var serverProfileId: String? = null,
    var serverLabel: String? = null
)

data class GetLocationsResponse(
    val status: String, val data: List<Vehicle>? = null, val message: String? = null
)

data class Member(
    val id: Int,
    val username: String,
    val role: String,
    @SerializedName("is_me") val isMe: Boolean = false
)

data class GetMembersResponse(
    val status: String,
    val data: List<Member>? = null,
    val message: String? = null
)

data class InviteCodeResponse(
    val status: String,
    val message: String? = null,
    val code: String? = null,
    @SerializedName("expires_at") val expiresAt: Long? = null,
    @SerializedName("vehicle_id") val vehicleId: Int? = null,
    @SerializedName("vehicle_name") val vehicleName: String? = null
)