package com.example.f_bank.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_payments")
data class FavoritePayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val userId: Long,
    val categoryId: String,
    val categoryTitle: String,
    val iconRes: Int,
    val lastUsedAt: Long,
    val createdAt: Long
)

