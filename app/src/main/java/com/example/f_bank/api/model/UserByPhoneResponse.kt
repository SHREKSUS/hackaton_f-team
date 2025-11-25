package com.example.f_bank.api.model

data class UserByPhoneResponse(
    val success: Boolean,
    val message: String? = null,
    val user: UserInfo? = null
)

data class UserInfo(
    val id: Long,
    val name: String,
    val phone: String
)

