package com.example.f_bank.api.model

data class CurrencyExchangeRequest(
    val fromCardId: Long,
    val fromCurrency: String,
    val toCurrency: String,
    val amount: Double
)

