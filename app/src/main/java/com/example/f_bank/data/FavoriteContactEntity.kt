package com.example.f_bank.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_contacts")
data class FavoriteContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val phone: String? = null,
    val cardNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toFavoriteContact(): FavoriteContact {
        val initials = generateInitials(name)
        return FavoriteContact(
            id = id,
            name = name,
            initials = initials,
            phone = phone,
            cardNumber = cardNumber
        )
    }
    
    private fun generateInitials(name: String): String {
        val parts = name.trim().split(" ")
        return when {
            parts.isEmpty() -> "??"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }
}

