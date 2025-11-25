package com.example.f_bank.api.model

data class CardsCheckResponse(
    val success: Boolean,
    val hasCards: Boolean,
    val count: Int = 0,
    val message: String? = null
)

