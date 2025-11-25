package com.example.f_bank.api.model

data class VerifyPinRequest(
    val userId: Long,
    val pin: String
)

