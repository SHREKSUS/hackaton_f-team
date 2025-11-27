package com.example.f_bank.api.model

data class VerifySmsCodeResponse(
    val success: Boolean,
    val message: String?,
    val token: String?,
    val user: VerifyUserData?
)

data class VerifyUserData(
    val id: Long,
    val name: String,
    val phone: String?
)

