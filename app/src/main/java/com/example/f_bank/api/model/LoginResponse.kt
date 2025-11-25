package com.example.f_bank.api.model

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: UserData? = null,
    val token: String? = null
)

data class UserData(
    val id: Long,
    val name: String,
    val phone: String? = null,  // Номер телефона вместо email
    val email: String? = null  // Для обратной совместимости с сервером
) {
    // Получаем телефон из email, если phone не указан (для обратной совместимости)
    fun getPhoneNumber(): String {
        return phone ?: email ?: ""
    }
}

