package com.example.f_bank.data

data class Transaction(
    val id: Long,
    val title: String,
    val time: String,
    val amount: Double,
    val type: TransactionType,
    val iconRes: Int,
    val category: TransactionCategory? = null,
    val timestamp: Long = 0L // Unix timestamp для фильтрации по периоду
)

enum class TransactionType {
    TRANSFER_OUT,
    TRANSFER_IN,
    DEPOSIT,
    PURCHASE
}

