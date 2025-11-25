package com.example.f_bank.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteContact(contact: FavoriteContactEntity): Long
    
    @Query("SELECT * FROM favorite_contacts WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getFavoriteContactsByUserId(userId: Long): List<FavoriteContactEntity>
    
    @Query("SELECT * FROM favorite_contacts WHERE userId = :userId AND phone = :phone LIMIT 1")
    suspend fun getFavoriteContactByPhone(userId: Long, phone: String): FavoriteContactEntity?
    
    @Query("SELECT * FROM favorite_contacts WHERE userId = :userId AND cardNumber = :cardNumber LIMIT 1")
    suspend fun getFavoriteContactByCardNumber(userId: Long, cardNumber: String): FavoriteContactEntity?
    
    @Delete
    suspend fun deleteFavoriteContact(contact: FavoriteContactEntity)
    
    @Query("DELETE FROM favorite_contacts WHERE id = :id")
    suspend fun deleteFavoriteContactById(id: Long)
}

