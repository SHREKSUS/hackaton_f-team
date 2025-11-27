package com.example.f_bank.api

import com.example.f_bank.api.model.CurrencyRatesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface FastForexApiService {
    // Согласно документации Fast Forex, API ключ передается в заголовке X-API-Key
    @GET("fetch-all")
    suspend fun getAllRates(
        @Header("X-API-Key") apiKey: String,
        @Query("from") from: String = "USD"
    ): Response<CurrencyRatesResponse>
    
    @GET("fetch-one")
    suspend fun getRate(
        @Header("X-API-Key") apiKey: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): Response<CurrencyRatesResponse>
}

