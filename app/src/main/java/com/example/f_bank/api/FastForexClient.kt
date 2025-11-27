package com.example.f_bank.api

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object FastForexClient {
    private const val BASE_URL = "https://api.fastforex.io/"
    private const val API_KEY = "105e3ea75f-cf36fa1a14-t6emev"
    
    // Кастомный DNS resolver для решения проблем с DNS на эмуляторе
    // Использует системный DNS lookup
    private val customDns = Dns.SYSTEM
    
    val apiService: FastForexApiService by lazy {
        // Настройка логирования HTTP запросов (показываем заголовки для проверки API ключа)
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            android.util.Log.d("FastForexClient", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        
        // Настройка OkHttp клиента с кастомным DNS
        val okHttpClient = OkHttpClient.Builder()
            .dns(customDns)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FastForexApiService::class.java)
    }
    
    fun getApiKey(): String = API_KEY
}

