package com.example.f_bank.api.model

data class CreateCardRequest(
    val type: String = "debit" // debit или credit
)

