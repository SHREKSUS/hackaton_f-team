package com.example.f_bank.api.model

data class SavePinRequest(
    val userId: Long,
    val pin: String
)

