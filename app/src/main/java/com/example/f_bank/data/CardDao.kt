package com.example.f_bank.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: Card): Long
    
    @Query("SELECT * FROM cards WHERE userId = :userId AND isDeleted = 0")
    suspend fun getCardsByUserId(userId: Long): List<Card>
    
    @Query("SELECT * FROM cards WHERE userId = :userId AND id = :cardId AND isDeleted = 0 LIMIT 1")
    suspend fun getCardById(userId: Long, cardId: Long): Card?
    
    @Query("SELECT * FROM cards WHERE userId = :userId AND number = :cardNumber AND isDeleted = 0 LIMIT 1")
    suspend fun getCardByNumber(userId: Long, cardNumber: String): Card?
    
    @Query("SELECT COUNT(*) FROM cards WHERE userId = :userId AND isDeleted = 0")
    suspend fun getCardCount(userId: Long): Int
    
    @Query("UPDATE cards SET isDeleted = 1 WHERE id = :cardId AND userId = :userId")
    suspend fun deleteCard(cardId: Long, userId: Long)
    
    @Query("UPDATE cards SET balance = :balance WHERE id = :cardId AND userId = :userId")
    suspend fun updateCardBalance(cardId: Long, userId: Long, balance: Double): Int
    
    @Query("UPDATE cards SET balance = :balance, type = :type, currency = :currency, expiry = :expiry WHERE userId = :userId AND number = :cardNumber")
    suspend fun updateCardByNumber(userId: Long, cardNumber: String, balance: Double, type: String, currency: String, expiry: String?)
    
    @Query("DELETE FROM cards WHERE userId = :userId AND number = :cardNumber")
    suspend fun deleteCardByNumber(userId: Long, cardNumber: String)
    
    @Query("SELECT * FROM cards WHERE userId = :userId AND isHidden = 0 AND isDeleted = 0 ORDER BY displayOrder ASC")
    suspend fun getVisibleCardsByUserId(userId: Long): List<Card>
    
    @Query("UPDATE cards SET displayOrder = :order WHERE id = :cardId AND userId = :userId")
    suspend fun updateCardOrder(cardId: Long, userId: Long, order: Int)
    
    @Query("UPDATE cards SET isHidden = :isHidden WHERE id = :cardId AND userId = :userId")
    suspend fun updateCardVisibility(cardId: Long, userId: Long, isHidden: Boolean)
    
    @Query("UPDATE cards SET displayOrder = :order WHERE id = :cardId")
    suspend fun updateCardOrderById(cardId: Long, order: Int)
    
    @Query("SELECT * FROM cards WHERE userId = :userId")
    suspend fun getAllCardsByUserId(userId: Long): List<Card>
    
    @Query("UPDATE cards SET isBlocked = :isBlocked WHERE id = :cardId AND userId = :userId")
    suspend fun updateCardBlockStatus(cardId: Long, userId: Long, isBlocked: Boolean)
    
    @Query("UPDATE cards SET dailyLimit = :limit WHERE id = :cardId AND userId = :userId")
    suspend fun updateCardDailyLimit(cardId: Long, userId: Long, limit: Double?)
    
    @Query("UPDATE cards SET monthlyLimit = :limit WHERE id = :cardId AND userId = :userId")
    suspend fun updateCardMonthlyLimit(cardId: Long, userId: Long, limit: Double?)
    
    @Query("UPDATE cards SET linkedPhone = :phone WHERE id = :cardId AND userId = :userId")
    suspend fun updateCardLinkedPhone(cardId: Long, userId: Long, phone: String?)
}

