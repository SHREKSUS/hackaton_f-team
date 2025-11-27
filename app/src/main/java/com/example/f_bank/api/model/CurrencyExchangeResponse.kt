package com.example.f_bank.api.model

data class CurrencyExchangeResponse(
    val success: Boolean,
    val message: String,
    val transactionId: Long? = null,
    val newBalance: Double? = null
)

