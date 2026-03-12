package com.cuscus.cussiparking.network

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface CussiParkingApi {

    @FormUrlEncoded
    @POST("register.php")
    suspend fun register(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("username") username: String
    ): Response<BaseResponse>

    @FormUrlEncoded
    @POST("login.php")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @FormUrlEncoded
    @POST("add_vehicle.php")
    suspend fun addVehicle(
        @Field("token") token: String,
        @Field("name") name: String,
        @Field("icon") icon: String
    ): Response<AddVehicleResponse>

    @FormUrlEncoded
    @POST("update_location.php")
    suspend fun updateLocation(
        @Field("token") token: String,
        @Field("vehicle_id") vehicleId: Int,
        @Field("lat") lat: Double,
        @Field("lng") lng: Double,
        @Field("updated_at") updatedAt: Long
    ): Response<BaseResponse>

    @FormUrlEncoded
    @POST("get_locations.php")
    suspend fun getLocations(
        @Field("token") token: String
    ): Response<GetLocationsResponse>

    @FormUrlEncoded
    @POST("delete_vehicle.php")
    suspend fun deleteVehicle(
        @Field("token") token: String,
        @Field("vehicle_id") vehicleId: Int
    ): Response<BaseResponse>

    @FormUrlEncoded
    @POST("get_members.php")
    suspend fun getMembers(
        @Field("token") token: String,
        @Field("vehicle_id") vehicleId: Int
    ): Response<GetMembersResponse>

    @FormUrlEncoded
    @POST("add_member.php")
    suspend fun addMember(
        @Field("token") token: String,
        @Field("vehicle_id") vehicleId: Int,
        @Field("username") username: String
    ): Response<BaseResponse>

    @FormUrlEncoded
    @POST("remove_member.php")
    suspend fun removeMember(
        @Field("token") token: String,
        @Field("vehicle_id") vehicleId: Int,
        @Field("target_user_id") targetUserId: Int
    ): Response<BaseResponse>

    @FormUrlEncoded
    @POST("change_role.php")
    suspend fun changeRole(
        @Field("token") token: String,
        @Field("vehicle_id") vehicleId: Int,
        @Field("target_user_id") targetUserId: Int,
        @Field("new_role") newRole: String
    ): Response<BaseResponse>

    @FormUrlEncoded
    @POST("delete_account.php")
    suspend fun deleteAccount(
        @Field("token") token: String,
        @Field("password") password: String
    ): Response<BaseResponse>

    @FormUrlEncoded
    @POST("invite_code.php")
    suspend fun inviteCode(
        @Field("token") token: String,
        @Field("action") action: String,
        @Field("vehicle_id") vehicleId: Int? = null,
        @Field("code") code: String? = null
    ): Response<InviteCodeResponse>
}