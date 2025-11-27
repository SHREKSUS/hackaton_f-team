package com.example.f_bank.api.model

import com.google.gson.annotations.SerializedName

data class CurrencyRatesResponse(
    @SerializedName("base")
    val base: String,
    @SerializedName("results")
    val results: Map<String, Double>,
    @SerializedName("updated")
    val updated: String,
    @SerializedName("ms")
    val ms: Int
)

