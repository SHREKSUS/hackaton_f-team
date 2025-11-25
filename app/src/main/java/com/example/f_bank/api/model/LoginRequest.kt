package com.example.f_bank.api.model

data class LoginRequest(
    val phone: String,  // Номер телефона вместо email
    val password: String
)

