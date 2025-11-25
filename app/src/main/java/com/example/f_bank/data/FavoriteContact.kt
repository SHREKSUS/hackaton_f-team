package com.example.f_bank.data

data class FavoriteContact(
    val id: Long,
    val name: String,
    val initials: String,
    val phone: String? = null,
    val cardNumber: String? = null
)

