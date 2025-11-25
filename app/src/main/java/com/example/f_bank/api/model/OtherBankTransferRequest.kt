package com.example.f_bank.api.model

data class OtherBankTransferRequest(
    val fromCardId: Long,
    val toCardNumber: String,
    val amount: Double,
    val description: String? = null
)

