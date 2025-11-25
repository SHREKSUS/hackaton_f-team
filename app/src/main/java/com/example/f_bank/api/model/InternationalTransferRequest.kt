package com.example.f_bank.api.model

data class InternationalTransferRequest(
    val fromCardId: Long,
    val transferSystem: String, // SWIFT, Western Union, Korona Pay
    val recipientName: String,
    val swiftCode: String? = null,
    val iban: String? = null,
    val receiverPhone: String? = null,
    val country: String,
    val amount: Double,
    val currency: String,
    val description: String? = null
)

