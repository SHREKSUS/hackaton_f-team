package com.example.f_bank.utils

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Утилита для шифрования и дешифрования номеров карт
 * Использует AES/GCM/NoPadding для безопасного шифрования
 */
object CardEncryption {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16
    private const val KEY_SIZE = 256
    
    /**
     * Генерирует ключ шифрования на основе PIN пользователя
     * Использует PIN как seed для генерации ключа
     */
    private fun generateKey(pin: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        val secureRandom = SecureRandom()
        // Используем PIN как seed для генерации ключа
        secureRandom.setSeed(pin.toByteArray())
        keyGenerator.init(KEY_SIZE, secureRandom)
        return keyGenerator.generateKey()
    }
    
    /**
     * Шифрует номер карты
     * @param cardNumber номер карты для шифрования
     * @param pin PIN пользователя для генерации ключа
     * @return зашифрованная строка в формате Base64 (IV + encrypted data)
     */
    fun encryptCardNumber(cardNumber: String, pin: String): String {
        try {
            val key = generateKey(pin)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            
            // Генерируем случайный IV для каждого шифрования
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
            
            val encrypted = cipher.doFinal(cardNumber.toByteArray(Charsets.UTF_8))
            
            // Объединяем IV и зашифрованные данные
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("Ошибка шифрования номера карты", e)
        }
    }
    
    /**
     * Проверяет, является ли номер карты зашифрованным
     * @param cardNumber номер карты (может быть зашифрованным или нет)
     * @return true если номер зашифрован, false если нет
     */
    fun isEncrypted(cardNumber: String): Boolean {
        return try {
            val decoded = Base64.decode(cardNumber, Base64.NO_WRAP)
            // Зашифрованные номера обычно длиннее обычных (минимум IV + данные)
            decoded.size > GCM_IV_LENGTH && cardNumber.length > 20
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Дешифрует номер карты
     * @param encryptedCardNumber зашифрованная строка в формате Base64 или обычный номер карты
     * @param pin PIN пользователя для генерации ключа
     * @return расшифрованный номер карты
     */
    fun decryptCardNumber(encryptedCardNumber: String, pin: String): String {
        // Если номер не зашифрован, возвращаем как есть
        if (!isEncrypted(encryptedCardNumber)) {
            return encryptedCardNumber
        }
        
        try {
            val key = generateKey(pin)
            val combined = Base64.decode(encryptedCardNumber, Base64.NO_WRAP)
            
            // Проверяем минимальную длину
            if (combined.size < GCM_IV_LENGTH) {
                return encryptedCardNumber // Возвращаем как есть, если слишком короткий
            }
            
            // Извлекаем IV и зашифрованные данные
            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            
            val encrypted = ByteArray(combined.size - iv.size)
            System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
            
            val decrypted = cipher.doFinal(encrypted)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            // Если не удалось расшифровать, возвращаем исходную строку
            // (возможно, это уже расшифрованный номер или старый формат)
            return encryptedCardNumber
        }
    }
    
    /**
     * Маскирует номер карты для отображения
     * @param cardNumber номер карты
     * @return замаскированный номер в формате "**** **** **** 1234"
     */
    fun maskCardNumber(cardNumber: String): String {
        val cleanNumber = cardNumber.replace(" ", "").replace("-", "")
        return if (cleanNumber.length >= 4) {
            "**** **** **** ${cleanNumber.substring(cleanNumber.length - 4)}"
        } else {
            "**** **** **** ****"
        }
    }
}

