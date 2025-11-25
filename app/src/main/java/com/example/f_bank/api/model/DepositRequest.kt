package com.example.f_bank.api.model

data class DepositRequest(
    val cardId: Long,
    val amount: Double
)

