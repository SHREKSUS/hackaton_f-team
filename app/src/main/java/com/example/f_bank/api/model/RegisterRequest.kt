package com.example.f_bank.api.model

data class RegisterRequest(
    val name: String,
    val phone: String,  // Номер телефона вместо email
    val password: String
)

