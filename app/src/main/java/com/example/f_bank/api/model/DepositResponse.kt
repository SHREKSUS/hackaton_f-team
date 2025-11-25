package com.example.f_bank.api.model

data class DepositResponse(
    val success: Boolean,
    val message: String? = null,
    val newBalance: Double? = null,
    val transactionId: Long? = null
)

