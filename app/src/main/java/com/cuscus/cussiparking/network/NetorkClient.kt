package com.cuscus.cussiparking.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {

    fun getApi(baseUrl: String): CussiParkingApi {

        // 1. Creiamo la spia che leggerà tutto il traffico
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Level.BODY stampa URL, Headers e il corpo in JSON (richiesta e risposta)
            level = HttpLoggingInterceptor.Level.BODY
        }

        // 2. Attacchiamo la spia al "motore" delle chiamate HTTP
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        // 3. Assicuriamoci che l'URL finisca sempre con una barra /
        val safeUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        // 4. Costruiamo Retrofit usando il nostro motore modificato
        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(client) // <-- ECCO IL TRUCCO!
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CussiParkingApi::class.java)
    }
}