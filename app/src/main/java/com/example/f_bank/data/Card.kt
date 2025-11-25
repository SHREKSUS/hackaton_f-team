package com.example.f_bank.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val number: String,
    val type: String, // debit, credit
    val balance: Double = 0.0,
    val currency: String = "KZT",
    val expiry: String? = null,
    val isLinked: Boolean = false, // true если карта привязана из другого банка
    val displayOrder: Int = 0, // Порядок отображения карт
    val isHidden: Boolean = false, // Скрыта ли карта
    val isDeleted: Boolean = false, // Удалена ли карта (soft delete)
    val isBlocked: Boolean = false, // Заблокирована ли карта
    val dailyLimit: Double? = null, // Дневной лимит
    val monthlyLimit: Double? = null, // Месячный лимит
    val linkedPhone: String? = null // Номер телефона, привязанный к карте для переводов
)

