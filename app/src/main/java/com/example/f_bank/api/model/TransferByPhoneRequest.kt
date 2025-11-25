package com.example.f_bank.api.model

data class TransferByPhoneRequest(
    val fromCardId: Long,
    val phone: String,
    val amount: Double,
    val description: String? = null
)

