package com.example.f_bank.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getTransactionsByUserId(userId: Long, limit: Int = 100): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE userId = :userId AND cardId = :cardId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getTransactionsByCardId(userId: Long, cardId: Long, limit: Int = 100): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAllTransactionsByUserId(userId: Long): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE userId = :userId AND category = :category ORDER BY timestamp DESC")
    suspend fun getTransactionsByCategory(userId: Long, category: String): List<TransactionEntity>
    
    @Query("SELECT * FROM transactions WHERE userId = :userId AND category IN (:categories) ORDER BY timestamp DESC")
    suspend fun getTransactionsByCategories(userId: Long, categories: List<String>): List<TransactionEntity>
    
    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllTransactionsByUserId(userId: Long)
}

