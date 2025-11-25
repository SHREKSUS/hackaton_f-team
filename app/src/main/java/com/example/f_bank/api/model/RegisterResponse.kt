package com.example.f_bank.api.model

data class RegisterResponse(
    val success: Boolean,
    val message: String,
    val userId: Long? = null,
    val token: String? = null
)

