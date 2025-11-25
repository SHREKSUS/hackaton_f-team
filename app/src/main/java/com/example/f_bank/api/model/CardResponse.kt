package com.example.f_bank.api.model

data class CardResponse(
    val success: Boolean,
    val message: String,
    val card: CardData? = null
)

data class CardData(
    val id: Long,
    val number: String,
    val type: String,
    val balance: Double,
    val currency: String? = "KZT",
    val expiry: String
)

