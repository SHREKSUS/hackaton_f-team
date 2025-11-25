package com.example.f_bank.api.model

data class LinkPhoneToCardRequest(
    val cardId: Long,
    val phone: String?
)

