package com.example.f_bank.api.model

data class TransferRequest(
    val fromCardId: Long,
    val toCardId: Long,
    val amount: Double,
    val description: String? = null
)

