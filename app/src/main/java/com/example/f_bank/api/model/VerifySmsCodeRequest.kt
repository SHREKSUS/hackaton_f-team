package com.example.f_bank.api.model

data class VerifySmsCodeRequest(
    val phone: String,
    val code: String
)

