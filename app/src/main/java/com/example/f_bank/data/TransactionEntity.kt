package com.example.f_bank.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val cardId: Long,
    val title: String,
    val amount: Double,
    val type: String, // TRANSFER_OUT, DEPOSIT, PURCHASE, TRANSFER_IN
    val timestamp: Long, // Unix timestamp
    val iconRes: Int = 0,
    val toCardId: Long? = null, // Для переводов между картами
    val fromCardId: Long? = null, // Для переводов между картами
    val category: String? = null // Категория транзакции
) {
    fun toTransaction(): Transaction? {
        return try {
            Transaction(
                id = id,
                title = title,
                time = formatDate(timestamp),
                amount = amount,
                type = TransactionType.valueOf(type),
                iconRes = iconRes,
                category = TransactionCategory.fromString(category),
                timestamp = timestamp
            )
        } catch (e: IllegalArgumentException) {
            // Если тип транзакции неизвестен, возвращаем null
            null
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val monthNames = arrayOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
        val month = monthNames[calendar.get(java.util.Calendar.MONTH)]
        return "$day $month"
    }
}

