package com.example.f_bank.utils

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

class SecurityPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Сохраняет PIN локально (для офлайн доступа)
     * ВАЖНО: PIN также должен быть сохранен на сервере через API
     */
    fun savePinLocally(pin: String) {
        // Используем commit() для гарантированного сохранения
        prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).commit()
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_PIN_HASH)

    /**
     * Проверяет PIN локально (для офлайн доступа)
     * ВАЖНО: Для полной проверки используйте API verifyPin
     */
    fun verifyPinLocally(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return stored == hashPin(pin)
    }
    
    // Оставляем старые методы для обратной совместимости
    fun savePin(pin: String) = savePinLocally(pin)
    fun verifyPin(pin: String) = verifyPinLocally(pin)

    fun setFingerprintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply()
    }

    fun isFingerprintEnabled(): Boolean =
        prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)

    fun saveCurrentUserId(userId: Long) {
        prefs.edit().putLong(KEY_CURRENT_USER_ID, userId).apply()
    }

    fun getCurrentUserId(): Long? {
        val userId = prefs.getLong(KEY_CURRENT_USER_ID, -1L)
        return if (userId == -1L) null else userId
    }
    
    fun saveAuthToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }
    
    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }
    
    fun clearAuthToken() {
        prefs.edit().remove(KEY_AUTH_TOKEN).apply()
    }

    /**
     * Сохраняет PIN для использования в шифровании карт
     * PIN сохраняется в зашифрованном виде (не как хеш)
     * ВАЖНО: Это менее безопасно чем хеш, но необходимо для шифрования карт
     */
    fun savePinForEncryption(pin: String) {
        // Сохраняем PIN в зашифрованном виде для использования в шифровании карт
        // Используем простой Base64 для минимальной защиты (в продакшене лучше использовать Android Keystore)
        val encoded = android.util.Base64.encodeToString(pin.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        prefs.edit().putString(KEY_PIN_FOR_ENCRYPTION, encoded).commit()
    }
    
    /**
     * Получает PIN для использования в шифровании карт
     */
    fun getPinForEncryption(): String? {
        val encoded = prefs.getString(KEY_PIN_FOR_ENCRYPTION, null) ?: return null
        return try {
            String(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Удаляет PIN для шифрования (при выходе из системы)
     */
    fun clearPinForEncryption() {
        prefs.edit().remove(KEY_PIN_FOR_ENCRYPTION).apply()
    }
    
    /**
     * Сохраняет время последнего перевода для предотвращения перезаписи балансов с сервера
     */
    fun saveLastTransferTime() {
        prefs.edit().putLong(KEY_LAST_TRANSFER_TIME, System.currentTimeMillis()).apply()
    }
    
    /**
     * Получает время последнего перевода
     */
    fun getLastTransferTime(): Long? {
        val time = prefs.getLong(KEY_LAST_TRANSFER_TIME, -1L)
        return if (time == -1L) null else time
    }
    
    /**
     * Проверяет, был ли недавний перевод (в течение последних 5 минут)
     */
    fun hasRecentTransfer(): Boolean {
        val lastTransferTime = getLastTransferTime() ?: return false
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        return lastTransferTime > fiveMinutesAgo
    }
    
    /**
     * Устанавливает флаг первого входа после регистрации
     */
    fun setFirstLoginAfterRegistration(isFirstLogin: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_LOGIN_AFTER_REGISTRATION, isFirstLogin).apply()
    }
    
    /**
     * Проверяет, является ли это первым входом после регистрации
     */
    fun isFirstLoginAfterRegistration(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LOGIN_AFTER_REGISTRATION, false)
    }

    companion object {
        private const val PREFS_NAME = "security_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_FOR_ENCRYPTION = "pin_for_encryption"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_LAST_TRANSFER_TIME = "last_transfer_time"
        private const val KEY_FIRST_LOGIN_AFTER_REGISTRATION = "first_login_after_registration"

        private fun hashPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(pin.toByteArray())
            val builder = StringBuilder()
            hash.forEach { byte ->
                builder.append(String.format(Locale.US, "%02x", byte))
            }
            return builder.toString()
        }
    }
}

