package com.example.f_bank.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface FavoritePaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoritePayment(payment: FavoritePayment): Long
    
    @Query("SELECT * FROM favorite_payments WHERE userId = :userId ORDER BY lastUsedAt DESC")
    suspend fun getFavoritePaymentsByUserId(userId: Long): List<FavoritePayment>
    
    @Query("SELECT * FROM favorite_payments WHERE userId = :userId AND categoryId = :categoryId LIMIT 1")
    suspend fun getFavoritePaymentByCategory(userId: Long, categoryId: String): FavoritePayment?
    
    @Delete
    suspend fun deleteFavoritePayment(payment: FavoritePayment)
    
    @Query("DELETE FROM favorite_payments WHERE userId = :userId AND categoryId = :categoryId")
    suspend fun deleteFavoritePaymentByCategory(userId: Long, categoryId: String)
    
    @Query("UPDATE favorite_payments SET lastUsedAt = :timestamp WHERE userId = :userId AND categoryId = :categoryId")
    suspend fun updateLastUsed(userId: Long, categoryId: String, timestamp: Long)
}

