package com.example.f_bank.api.model

data class TransferResponse(
    val success: Boolean,
    val message: String,
    val newBalance: Double? = null,
    val transactionId: Long? = null
)

