package com.example.f_bank.data

import android.content.Context
import android.util.Log
import com.example.f_bank.api.RetrofitClient
import com.example.f_bank.api.model.CardData
import com.example.f_bank.api.model.LoginRequest
import com.example.f_bank.api.model.LoginResponse
import com.example.f_bank.api.model.RegisterRequest
import com.example.f_bank.api.model.RegisterResponse
import com.example.f_bank.api.model.TransferRequest
import com.example.f_bank.api.model.TransferByPhoneRequest
import com.example.f_bank.api.model.TransferResponse
import com.example.f_bank.api.model.OtherBankTransferRequest
import com.example.f_bank.api.model.InternationalTransferRequest
import com.example.f_bank.api.model.UserByPhoneResponse
import com.example.f_bank.api.model.GetUserByPhoneRequest
import com.example.f_bank.api.model.DepositRequest
import com.example.f_bank.api.model.DepositResponse
import com.example.f_bank.data.Card
import com.example.f_bank.utils.CardEncryption
import com.example.f_bank.utils.SecurityPreferences
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(context: Context) {
    // Оставляем Room для офлайн кэширования (опционально)
    private val userDao = AppDatabase.getDatabase(context).userDao()
    private val cardDao = AppDatabase.getDatabase(context).cardDao()
    private val apiService = RetrofitClient.apiService
    private val securityPreferences = SecurityPreferences(context)
    private val gson = Gson()
    private var lastLoginResponse: LoginResponse? = null
    private var lastRegisterResponse: RegisterResponse? = null
    @Volatile
    private var isLoggingIn = false
    
    suspend fun registerUser(name: String, phone: String, password: String): Result<Long> {
        return withContext(Dispatchers.IO) {
            // Проверяем локально дубликаты перед обращением к серверу
            val existingLocal = userDao.getUserByPhone(phone)
            if (existingLocal != null) {
                return@withContext Result.failure(Exception("Пользователь с таким номером телефона уже существует"))
            }

            // Нормализуем номер телефона перед отправкой на сервер
            val normalizedPhone = normalizePhoneNumber(phone)
            
            // Пробуем зарегистрироваться на сервере
            try {
                val request = RegisterRequest(name = name, phone = normalizedPhone, password = password)
                val response = apiService.register(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val registerResponse = response.body()!!
                    lastRegisterResponse = registerResponse // Сохраняем ответ для получения токена
                    
                    if (registerResponse.success && registerResponse.userId != null) {
                        // Сохраняем пользователя локально
                        val user = User(
                            id = registerResponse.userId,
                            name = name,
                            phone = normalizedPhone, // Используем нормализованный номер
                            password = password // В реальном приложении не храните пароль
                        )
                        userDao.insertUser(user)
                        
                        return@withContext Result.success(registerResponse.userId)
                    } else {
                        // Сервер вернул ошибку (success=false)
                        val errorMsg = registerResponse.message?.takeIf { it.isNotBlank() } 
                            ?: "Ошибка регистрации"
                        return@withContext Result.failure(Exception(errorMsg))
                    }
                } else {
                    // HTTP ошибка - парсим JSON из errorBody
                    val errorMessage = try {
                        val errorBodyString = response.errorBody()?.string()
                        if (errorBodyString != null && errorBodyString.isNotBlank()) {
                            try {
                                val errorResponse = gson.fromJson(errorBodyString, RegisterResponse::class.java)
                                errorResponse.message?.takeIf { it.isNotBlank() } ?: "Ошибка регистрации (код ${response.code()})"
                            } catch (e: Exception) {
                                // Если не удалось распарсить как RegisterResponse, пробуем как строку
                                errorBodyString.takeIf { it.length < 200 } ?: "Ошибка регистрации (код ${response.code()})"
                            }
                        } else {
                            when (response.code()) {
                                400 -> "Неверные данные для регистрации"
                                401 -> "Ошибка авторизации"
                                500 -> "Ошибка на сервере. Попробуйте позже"
                                else -> "Ошибка регистрации (код ${response.code()})"
                            }
                        }
                    } catch (e: Exception) {
                        "Ошибка регистрации. Проверьте подключение к серверу."
                    }
                    return@withContext Result.failure(Exception(errorMessage))
                }
            } catch (e: CancellationException) {
                // При отмене корутины пробрасываем исключение дальше
                throw e
            } catch (e: java.net.UnknownHostException) {
                // Сервер недоступен
                return@withContext Result.failure(Exception("Сервер недоступен. Проверьте, что сервер запущен и доступен по адресу http://10.0.2.2:5000"))
            } catch (e: java.net.SocketTimeoutException) {
                // Таймаут подключения
                return@withContext Result.failure(Exception("Превышено время ожидания ответа сервера. Проверьте подключение к интернету"))
            } catch (e: java.io.IOException) {
                // Ошибка сети
                return@withContext Result.failure(Exception("Ошибка подключения к серверу: ${e.message ?: "Проверьте подключение к интернету"}"))
            } catch (e: Exception) {
                // Другие ошибки
                val errorMsg = e.message ?: "Неизвестная ошибка при регистрации"
                Log.e("UserRepository", "Registration error: $errorMsg", e)
                return@withContext Result.failure(Exception("Ошибка регистрации: $errorMsg"))
            }
        }
    }
    
    /**
     * Нормализует номер телефона для отправки на сервер
     * Сервер ожидает 11 цифр, начинающихся с 7, или 10 цифр (тогда добавляется 7)
     */
    private fun normalizePhoneNumber(phone: String): String {
        // Убираем все нецифровые символы
        val digitsOnly = phone.replace(Regex("[^0-9]"), "")
        
        return when {
            digitsOnly.length == 11 && digitsOnly.startsWith("7") -> digitsOnly
            digitsOnly.length == 10 -> "7$digitsOnly"
            digitsOnly.length == 11 && digitsOnly.startsWith("8") -> "7${digitsOnly.substring(1)}"
            else -> digitsOnly // Возвращаем как есть, сервер проверит формат
        }
    }
    
    suspend fun loginUser(phone: String, password: String): Result<User> {
        // Предотвращаем параллельные вызовы логина
        if (isLoggingIn) {
            return Result.failure(Exception("Вход уже выполняется"))
        }
        
        isLoggingIn = true
        try {
            return withContext(Dispatchers.IO) {
                try {
                    val request = LoginRequest(phone = phone, password = password)
                    val response = apiService.login(request)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val loginResponse = response.body()!!
                        lastLoginResponse = loginResponse // Сохраняем ответ для получения токена
                        
                        android.util.Log.d("UserRepository", "Login response: success=${loginResponse.success}, user=${loginResponse.user}, token=${loginResponse.token != null}")
                        
                        if (loginResponse.success) {
                            // Проверяем, есть ли пользователь в ответе
                            val userData = loginResponse.user
                            android.util.Log.d("UserRepository", "UserData: $userData")
                            if (userData != null) {
                                // Проверяем, есть ли уже пользователь в локальной БД
                                val existingUser = userDao.getUserById(userData.id)
                                
                                // Получаем телефон из userData (может быть в phone или email поле)
                                val userPhone = userData.getPhoneNumber()
                                
                                // Всегда обновляем имя пользователя с сервера при логине
                                // Сохраняем только пароль из локальной БД, если он есть
                                val user = User(
                                    id = userData.id,
                                    name = userData.name, // Всегда используем имя с сервера
                                    phone = userPhone,
                                    password = existingUser?.password ?: "" // Сохраняем локальный пароль если есть
                                )
                                
                                android.util.Log.d("UserRepository", "Updating user name from server: ${userData.name}")
                                
                                // Сохраняем в локальную БД для офлайн доступа
                                userDao.insertUser(user)
                                
                                Result.success(user)
                            } else {
                                // Если пользователь не в ответе, но success=true - это ошибка
                                Result.failure(Exception("Данные пользователя не получены с сервера"))
                            }
                        } else {
                            Result.failure(Exception(loginResponse.message))
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = errorBody ?: response.message() ?: "Ошибка при входе"
                        Result.failure(Exception(errorMessage))
                    }
                } catch (e: Exception) {
                    // Fallback: пробуем локальную БД если нет интернета
                    try {
                        val localUser = userDao.getUserByPhoneAndPassword(phone, password)
                        if (localUser != null) {
                            Result.success(localUser)
                        } else {
                            Result.failure(Exception("Ошибка подключения к серверу и пользователь не найден локально"))
                        }
                    } catch (localError: Exception) {
                        Result.failure(Exception("Ошибка подключения к серверу: ${e.message}"))
                    }
                }
            }
        } finally {
            isLoggingIn = false
        }
    }
    
    suspend fun getUserById(userId: Long): User? {
        return withContext(Dispatchers.IO) {
            // Сначала пробуем локальную БД
            val localUser = userDao.getUserById(userId)
            if (localUser != null) {
                localUser
            } else {
                // Если нет локально, можно сделать запрос к API
                null
            }
        }
    }
    
    fun getLastLoginResponse(): LoginResponse? = lastLoginResponse
    fun getLastRegisterResponse(): RegisterResponse? = lastRegisterResponse
    
    suspend fun checkCards(token: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.checkCards("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val checkResponse = response.body()!!
                    Result.success(checkResponse.hasCards)
                } else {
                    // По умолчанию считаем, что карт нет
                    Result.success(false)
                }
            } catch (e: Exception) {
                // При ошибке считаем, что карт нет
                Result.success(false)
            }
        }
    }
    
    suspend fun createCard(token: String, userId: Long, cardType: String = "debit"): Result<CardData> {
        return withContext(Dispatchers.IO) {
            try {
                val request = com.example.f_bank.api.model.CreateCardRequest(type = cardType)
                val response = apiService.createCard("Bearer $token", request)
                if (response.isSuccessful && response.body() != null) {
                    val cardResponse = response.body()!!
                    if (cardResponse.success && cardResponse.card != null) {
                        val cardData = cardResponse.card!!
                        
                        // Номер карты уже расшифрован сервером
                        // Сохраняем номер карты как есть (без дополнительного шифрования)
                        // так как сервер уже шифрует его в БД
                        val cardNumberToStore = cardData.number
                        
                        // Сохраняем карту в локальную БД
                        // Получаем текущее количество карт для установки порядка
                        val existingCards = cardDao.getCardsByUserId(userId)
                        val card = Card(
                            userId = userId,
                            number = cardNumberToStore,
                            type = cardData.type,
                            balance = cardData.balance,
                            currency = cardData.currency ?: "KZT",
                            expiry = cardData.expiry,
                            isLinked = false,
                            displayOrder = existingCards.size,
                            isHidden = false,
                            isDeleted = false
                        )
                        val cardId = cardDao.insertCard(card)
                        
                        // Проверяем, что карта успешно сохранена
                        if (cardId > 0) {
                            // Карта успешно сохранена
                        }
                        
                        Result.success(cardData)
                    } else {
                        Result.failure(Exception(cardResponse.message))
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Result.failure(Exception(errorBody ?: "Ошибка при создании карты"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun linkCard(userId: Long, cardNumber: String, expiry: String, cvv: String, cardholderName: String): Result<CardData> {
        return withContext(Dispatchers.IO) {
            try {
                // Валидация номера карты (убираем пробелы)
                val cleanCardNumber = cardNumber.replace(" ", "").replace("-", "")
                if (cleanCardNumber.length != 16 || !cleanCardNumber.all { it.isDigit() }) {
                    return@withContext Result.failure(Exception("Неверный формат номера карты"))
                }
                
                // Валидация CVV (3-4 цифры)
                if (cvv.length < 3 || cvv.length > 4 || !cvv.all { it.isDigit() }) {
                    return@withContext Result.failure(Exception("CVV должен содержать 3-4 цифры"))
                }
                
                // Валидация срока действия (MM/YY)
                if (!expiry.matches(Regex("\\d{2}/\\d{2}"))) {
                    return@withContext Result.failure(Exception("Неверный формат срока действия (MM/YY)"))
                }
                
                // Форматируем номер карты
                val formattedNumber = "${cleanCardNumber.substring(0, 4)} ${cleanCardNumber.substring(4, 8)} ${cleanCardNumber.substring(8, 12)} ${cleanCardNumber.substring(12, 16)}"
                
                // Шифруем номер карты перед сохранением
                val pin = securityPreferences.getPinForEncryption()
                val cardNumberToStore = if (pin != null) {
                    CardEncryption.encryptCardNumber(formattedNumber, pin)
                } else {
                    // Если PIN не найден, сохраняем как есть (для обратной совместимости)
                    formattedNumber
                }
                
                // Получаем текущее количество карт для установки порядка
                val existingCards = cardDao.getCardsByUserId(userId)
                // Создаем карту локально (привязанная карта)
                val card = Card(
                    userId = userId,
                    number = cardNumberToStore,
                    type = "debit", // По умолчанию дебетовая
                    balance = 0.0,
                    currency = "KZT",
                    expiry = expiry,
                    isLinked = true,
                    displayOrder = existingCards.size,
                    isHidden = false,
                    isDeleted = false
                )
                val cardId = cardDao.insertCard(card)
                
                // Возвращаем CardData для совместимости с API
                val cardData = CardData(
                    id = cardId,
                    number = formattedNumber,
                    type = "debit",
                    balance = 0.0,
                    currency = "KZT",
                    expiry = expiry
                )
                
                Result.success(cardData)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun linkCardByPhone(userId: Long, phone: String): Result<CardData> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securityPreferences.getAuthToken()
                if (token == null) {
                    return@withContext Result.failure(Exception("Токен авторизации не найден"))
                }
                
                // Нормализуем номер телефона
                val normalizedPhone = normalizePhoneNumber(phone)
                
                // Вызываем API для привязки карты по номеру телефона
                val request = com.example.f_bank.api.model.LinkCardByPhoneRequest(
                    userId = userId,
                    phone = normalizedPhone
                )
                val response = apiService.linkCardByPhone(token, request)
                
                if (response.isSuccessful && response.body() != null) {
                    val cardResponse = response.body()!!
                    if (cardResponse.success && cardResponse.card != null) {
                        val cardData = cardResponse.card!!
                        
                        // Шифруем номер карты перед сохранением локально
                        val pin = securityPreferences.getPinForEncryption()
                        val cardNumberToStore = if (pin != null) {
                            CardEncryption.encryptCardNumber(cardData.number, pin)
                        } else {
                            cardData.number
                        }
                        
                        // Получаем текущее количество карт для установки порядка
                        val existingCards = cardDao.getCardsByUserId(userId)
                        
                        // Сохраняем карту локально
                        val card = Card(
                            userId = userId,
                            number = cardNumberToStore,
                            type = cardData.type ?: "debit",
                            balance = cardData.balance ?: 0.0,
                            currency = cardData.currency ?: "KZT",
                            expiry = cardData.expiry ?: "12/25",
                            isLinked = true,
                            displayOrder = existingCards.size,
                            isHidden = false,
                            isDeleted = false
                        )
                        cardDao.insertCard(card)
                        
                        Result.success(cardData)
                    } else {
                        val errorMsg = cardResponse.message?.takeIf { it.isNotBlank() } 
                            ?: "Ошибка при привязке карты по номеру телефона"
                        Result.failure(Exception(errorMsg))
                    }
                } else {
                    // HTTP ошибка
                    val errorMessage = try {
                        val errorBodyString = response.errorBody()?.string()
                        if (errorBodyString != null && errorBodyString.isNotBlank()) {
                            try {
                                val errorResponse = gson.fromJson(errorBodyString, com.example.f_bank.api.model.CardResponse::class.java)
                                errorResponse.message?.takeIf { it.isNotBlank() } ?: "Ошибка при привязке карты (код ${response.code()})"
                            } catch (e: Exception) {
                                errorBodyString.takeIf { it.length < 200 } ?: "Ошибка при привязке карты (код ${response.code()})"
                            }
                        } else {
                            when (response.code()) {
                                400 -> "Неверный номер телефона или карта не найдена"
                                404 -> "Карта с таким номером телефона не найдена"
                                500 -> "Ошибка на сервере. Попробуйте позже"
                                else -> "Ошибка при привязке карты (код ${response.code()})"
                            }
                        }
                    } catch (e: Exception) {
                        "Ошибка при привязке карты. Проверьте подключение к серверу."
                    }
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: java.net.UnknownHostException) {
                Result.failure(Exception("Сервер недоступен. Проверьте подключение к интернету."))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(Exception("Превышено время ожидания ответа от сервера."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getCards(userId: Long): Result<List<Card>> {
        return withContext(Dispatchers.IO) {
            try {
                val cards = cardDao.getCardsByUserId(userId)
                // Номера карт уже расшифрованы сервером перед отправкой клиенту
                // и сохранены в локальной БД без дополнительного шифрования
                // Поэтому просто возвращаем карты как есть
                Result.success(cards)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getVisibleCards(userId: Long): Result<List<Card>> {
        return withContext(Dispatchers.IO) {
            try {
                val cards = cardDao.getVisibleCardsByUserId(userId)
                Result.success(cards)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun updateCardOrder(cardId: Long, userId: Long, order: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cardDao.updateCardOrder(cardId, userId, order)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun updateCardVisibility(cardId: Long, userId: Long, isHidden: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cardDao.updateCardVisibility(cardId, userId, isHidden)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun deleteCard(cardId: Long, userId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cardDao.deleteCard(cardId, userId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun updateCardBlockStatus(cardId: Long, userId: Long, isBlocked: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cardDao.updateCardBlockStatus(cardId, userId, isBlocked)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun updateCardDailyLimit(cardId: Long, userId: Long, limit: Double?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cardDao.updateCardDailyLimit(cardId, userId, limit)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun updateCardMonthlyLimit(cardId: Long, userId: Long, limit: Double?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                cardDao.updateCardMonthlyLimit(cardId, userId, limit)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun linkPhoneToCard(cardId: Long, userId: Long, phone: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securityPreferences.getAuthToken()
                if (token != null) {
                    // Вызываем API для привязки номера телефона к карте
                    val request = com.example.f_bank.api.model.LinkPhoneToCardRequest(
                        cardId = cardId,
                        phone = phone
                    )
                    val response = apiService.linkPhoneToCard(token, request)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val linkResponse = response.body()!!
                        if (linkResponse.success) {
                            // Обновляем локально
                            cardDao.updateCardLinkedPhone(cardId, userId, phone)
                            Result.success(Unit)
                        } else {
                            val errorMsg = linkResponse.message?.takeIf { it.isNotBlank() } 
                                ?: "Ошибка при привязке номера телефона"
                            Result.failure(Exception(errorMsg))
                        }
                    } else {
                        // HTTP ошибка - все равно обновляем локально
                        cardDao.updateCardLinkedPhone(cardId, userId, phone)
                        val errorMessage = try {
                            val errorBodyString = response.errorBody()?.string()
                            if (errorBodyString != null && errorBodyString.isNotBlank()) {
                                try {
                                    val errorResponse = gson.fromJson(errorBodyString, com.example.f_bank.api.model.CardResponse::class.java)
                                    errorResponse.message?.takeIf { it.isNotBlank() } ?: "Ошибка при привязке номера телефона (код ${response.code()})"
                                } catch (e: Exception) {
                                    errorBodyString.takeIf { it.length < 200 } ?: "Ошибка при привязке номера телефона (код ${response.code()})"
                                }
                            } else {
                                when (response.code()) {
                                    400 -> "Неверный номер телефона или карта не найдена"
                                    404 -> "Карта не найдена"
                                    500 -> "Ошибка на сервере. Попробуйте позже"
                                    else -> "Ошибка при привязке номера телефона (код ${response.code()})"
                                }
                            }
                        } catch (e: Exception) {
                            "Ошибка при привязке номера телефона. Проверьте подключение к серверу."
                        }
                        Result.failure(Exception(errorMessage))
                    }
                } else {
                    // Если нет токена, обновляем только локально
                    cardDao.updateCardLinkedPhone(cardId, userId, phone)
                    Result.success(Unit)
                }
            } catch (e: java.net.UnknownHostException) {
                // Сервер недоступен - обновляем локально
                cardDao.updateCardLinkedPhone(cardId, userId, phone)
                Result.success(Unit)
            } catch (e: java.net.SocketTimeoutException) {
                // Таймаут - обновляем локально
                cardDao.updateCardLinkedPhone(cardId, userId, phone)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun transferBetweenCards(fromCardId: Long, toCardId: Long, amount: Double, description: String?): Result<TransferResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securityPreferences.getAuthToken()
                if (token == null) {
                    return@withContext Result.failure(Exception("Токен авторизации не найден"))
                }
                
                val request = TransferRequest(
                    fromCardId = fromCardId,
                    toCardId = toCardId,
                    amount = amount,
                    description = description
                )
                
                val response = apiService.transferBetweenCards("Bearer $token", request)
                
                if (response.isSuccessful && response.body() != null) {
                    val transferResponse = response.body()!!
                    
                    // Обновляем балансы карт в локальной БД
                    val userId = securityPreferences.getCurrentUserId()
                    if (userId != null && transferResponse.newBalance != null) {
                        // Обновляем баланс карты отправителя напрямую
                        val rowsUpdatedFrom = cardDao.updateCardBalance(fromCardId, userId, transferResponse.newBalance)
                        android.util.Log.d("UserRepository", "Updated fromCard balance: rows=$rowsUpdatedFrom")
                        
                        // Получаем текущий баланс карты получателя и увеличиваем его
                        val toCard = cardDao.getCardById(userId, toCardId)
                        if (toCard != null) {
                            val newToBalance = toCard.balance + amount
                            val rowsUpdatedTo = cardDao.updateCardBalance(toCardId, userId, newToBalance)
                            android.util.Log.d("UserRepository", "Updated toCard balance: rows=$rowsUpdatedTo")
                        }
                        
                        // Синхронизируем карты с сервером для получения актуальных данных
                        syncCardsFromServer(userId, token) // Игнорируем результат, так как балансы уже обновлены
                    }
                    
                    Result.success(transferResponse)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Неизвестная ошибка"
                    Result.failure(Exception("Ошибка перевода: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Ошибка при переводе: ${e.message}"))
            }
        }
    }
    
    suspend fun transferByPhone(fromCardId: Long, phone: String, amount: Double, description: String?): Result<TransferResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securityPreferences.getAuthToken()
                if (token == null) {
                    return@withContext Result.failure(Exception("Токен авторизации не найден"))
                }
                
                val request = TransferByPhoneRequest(
                    fromCardId = fromCardId,
                    phone = phone,
                    amount = amount,
                    description = description
                )
                
                val response = apiService.transferByPhone("Bearer $token", request)
                
                if (response.isSuccessful && response.body() != null) {
                    val transferResponse = response.body()!!
                    
                    // Обновляем баланс карты отправителя в локальной БД
                    val userId = securityPreferences.getCurrentUserId()
                    if (userId != null && transferResponse.newBalance != null) {
                        val rowsUpdated = cardDao.updateCardBalance(fromCardId, userId, transferResponse.newBalance)
                        android.util.Log.d("UserRepository", "Updated fromCard balance after phone transfer: rows=$rowsUpdated")
                        
                        // Синхронизируем карты с сервером для получения актуальных данных
                        syncCardsFromServer(userId, token)
                    }
                    
                    Result.success(transferResponse)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Неизвестная ошибка"
                    Result.failure(Exception("Ошибка перевода по номеру телефона: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Ошибка при переводе по номеру телефона: ${e.message}"))
            }
        }
    }
    
    suspend fun getUserByPhone(phone: String): Result<String?> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securityPreferences.getAuthToken()
                if (token == null) {
                    return@withContext Result.failure(Exception("Токен авторизации не найден"))
                }
                
                // Нормализуем номер телефона
                val normalizedPhone = normalizePhoneNumber(phone)
                
                val request = GetUserByPhoneRequest(phone = normalizedPhone)
                val response = apiService.getUserByPhone("Bearer $token", request)
                
                if (response.isSuccessful && response.body() != null) {
                    val userResponse = response.body()!!
                    if (userResponse.success && userResponse.user != null) {
                        Result.success(userResponse.user.name)
                    } else {
                        Result.success(null) // Пользователь не найден
                    }
                } else {
                    // Если пользователь не найден (404), возвращаем null
                    if (response.code() == 404) {
                        Result.success(null)
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Неизвестная ошибка"
                        Result.failure(Exception("Ошибка получения пользователя: $errorBody"))
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                Result.failure(Exception("Сервер недоступен. Проверьте подключение к интернету."))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(Exception("Превышено время ожидания ответа от сервера."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception("Ошибка при получении пользователя: ${e.message}"))
            }
        }
    }
    
    suspend fun transferToOtherBank(fromCardId: Long, toCardNumber: String, amount: Double, description: String?): Result<TransferResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securityPreferences.getAuthToken()
                if (token == null) {
                    return@withContext Result.failure(Exception("Токен авторизации не найден"))
                }
                
                // Очищаем номер карты от пробелов и дефисов
                val cleanCardNumber = toCardNumber.replace(" ", "").replace("-", "")
                
                val request = OtherBankTransferRequest(
                    fromCardId = fromCardId,
                    toCardNumber = cleanCardNumber,
                    amount = amount,
                    description = description
                )
                
                val response = apiService.transferToOtherBank("Bearer $token", request)
                
                if (response.isSuccessful && response.body() != null) {
                    val transferResponse = response.body()!!
                    
                    // Обновляем баланс карты отправителя в локальной БД
                    val userId = securityPreferences.getCurrentUserId()
                    if (userId != null && transferResponse.newBalance != null) {
                        val rowsUpdated = cardDao.updateCardBalance(fromCardId, userId, transferResponse.newBalance)
                        android.util.Log.d("UserRepository", "Updated fromCard balance after other bank transfer: rows=$rowsUpdated")
                        
                        // Синхронизируем карты с сервером для получения актуальных данных
                        syncCardsFromServer(userId, token)
                    }
                    
                    Result.success(transferResponse)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Неизвестная ошибка"
                    Result.failure(Exception("Ошибка перевода в другой банк: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Ошибка при переводе в другой банк: ${e.message}"))
            }
        }
    }

    suspend fun internationalTransfer(
        fromCardId: Long,
        transferSystem: String,
        recipientName: String,
        swiftCode: String?,
        iban: String?,
        receiverPhone: String?,
        country: String,
        amount: Double,
        currency: String,
        description: String?
    ): Result<TransferResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securityPreferences.getAuthToken()
                if (token == null) {
                    return@withContext Result.failure(Exception("Токен авторизации не найден"))
                }

                val request = InternationalTransferRequest(
                    fromCardId = fromCardId,
                    transferSystem = transferSystem,
                    recipientName = recipientName,
                    swiftCode = swiftCode?.uppercase(),
                    iban = iban?.uppercase(),
                    receiverPhone = receiverPhone,
                    country = country,
                    amount = amount,
                    currency = currency,
                    description = description
                )

                val response = apiService.internationalTransfer("Bearer $token", request)

                if (response.isSuccessful && response.body() != null) {
                    val transferResponse = response.body()!!

                    // Обновляем баланс карты отправителя в локальной БД
                    val userId = securityPreferences.getCurrentUserId()
                    if (userId != null && transferResponse.newBalance != null) {
                        val rowsUpdated = cardDao.updateCardBalance(fromCardId, userId, transferResponse.newBalance)
                        android.util.Log.d("UserRepository", "Updated fromCard balance after international transfer: rows=$rowsUpdated")

                        // Синхронизируем карты с сервером для получения актуальных данных
                        syncCardsFromServer(userId, token)
                    }

                    Result.success(transferResponse)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Неизвестная ошибка"
                    Result.failure(Exception("Ошибка международного перевода: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Ошибка при международном переводе: ${e.message}"))
            }
        }
    }

    suspend fun depositToCard(cardId: Long, amount: Double): Result<DepositResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val token = securityPreferences.getAuthToken()
                if (token == null) {
                    return@withContext Result.failure(Exception("Токен авторизации не найден"))
                }
                
                val request = DepositRequest(cardId = cardId, amount = amount)
                val response = apiService.depositToCard("Bearer $token", request)
                
                if (response.isSuccessful && response.body() != null) {
                    val depositResponse = response.body()!!
                    if (depositResponse.success) {
                        // Обновляем баланс карты локально
                        depositResponse.newBalance?.let { newBalance ->
                            cardDao.updateCardBalance(cardId, securityPreferences.getCurrentUserId() ?: 0, newBalance)
                        }
                        
                        // Синхронизируем карты с сервером для обновления всех данных
                        securityPreferences.getCurrentUserId()?.let { userId ->
                            syncCardsFromServer(userId, token)
                        }
                        
                        Result.success(depositResponse)
                    } else {
                        Result.failure(Exception(depositResponse.message ?: "Ошибка пополнения"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Неизвестная ошибка"
                    Result.failure(Exception("Ошибка пополнения: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Ошибка при пополнении: ${e.message}"))
            }
        }
    }

    suspend fun syncCardsFromServer(userId: Long, token: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCards("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val cardsResponse = response.body()!!
                    if (cardsResponse.success) {
                        // Получаем список номеров карт с сервера для проверки существования
                        val serverCardNumbers = cardsResponse.cards.map { it.number.replace(" ", "").replace("-", "") }.toSet()
                        
                        // Получаем все карты включая удаленные для проверки
                        val allLocalCards = cardDao.getAllCardsByUserId(userId)
                        
                        // Обновляем или создаем карты из сервера
                        cardsResponse.cards.forEach { cardData ->
                            // Проверяем, существует ли карта по номеру (включая удаленные)
                            val normalizedNumber = cardData.number.replace(" ", "").replace("-", "")
                            var existingCard = allLocalCards.find { 
                                it.number.replace(" ", "").replace("-", "") == normalizedNumber 
                            }
                            
                            // Если карта была удалена локально, не восстанавливаем её
                            if (existingCard != null && existingCard.isDeleted) {
                                // Пропускаем удаленные карты
                                return@forEach
                            }
                            
                            if (existingCard != null) {
                                // Обновляем существующую карту по номеру из БД (не создаем дубликат)
                                // ВАЖНО: Если ID карты не совпадает с сервером, удаляем старую и создаем новую с правильным ID
                                // Используем номер карты из существующей записи для точного совпадения
                                // НЕ обновляем баланс, если он был изменен локально недавно (в течение последних 5 минут)
                                // Это предотвращает перезапись балансов после локального перевода
                                val shouldUpdateBalance = !securityPreferences.hasRecentTransfer()
                                
                                // Проверяем, нужно ли обновить ID карты
                                val needsIdUpdate = existingCard.id != cardData.id
                                if (needsIdUpdate) {
                                    android.util.Log.d("UserRepository", "Card ID mismatch: local=${existingCard.id}, server=${cardData.id}. Updating card ${existingCard.number.takeLast(4)}")
                                    // Удаляем старую карту и создаем новую с правильным ID
                                    cardDao.deleteCardByNumber(userId, existingCard.number)
                                    // Создаем новую карту с правильным ID с сервера
                                    val balanceToUse = if (shouldUpdateBalance) cardData.balance else existingCard.balance
                                    val updatedCard = Card(
                                        id = cardData.id, // Используем ID с сервера
                                        userId = userId,
                                        number = cardData.number,
                                        type = cardData.type,
                                        balance = balanceToUse,
                                        currency = cardData.currency ?: "KZT",
                                        expiry = cardData.expiry,
                                        isLinked = existingCard.isLinked,
                                        displayOrder = existingCard.displayOrder,
                                        isHidden = existingCard.isHidden,
                                        isDeleted = existingCard.isDeleted
                                    )
                                    cardDao.insertCard(updatedCard)
                                    android.util.Log.d("UserRepository", "Card recreated with server ID: ${cardData.id}")
                                } else {
                                    // ID совпадает, просто обновляем остальные поля
                                    if (shouldUpdateBalance) {
                                        // Обновляем баланс только если не было недавнего перевода
                                        cardDao.updateCardByNumber(
                                            userId = userId,
                                            cardNumber = existingCard.number,
                                            balance = cardData.balance,
                                            type = cardData.type,
                                            currency = cardData.currency ?: "KZT",
                                            expiry = cardData.expiry
                                        )
                                    } else {
                                        // Обновляем все поля кроме баланса, сохраняя локальный баланс
                                        android.util.Log.d("UserRepository", "Skipping balance update for card ${existingCard.number.takeLast(4)} due to recent transfer")
                                        cardDao.updateCardByNumber(
                                            userId = userId,
                                            cardNumber = existingCard.number,
                                            balance = existingCard.balance, // Сохраняем локальный баланс
                                            type = cardData.type,
                                            currency = cardData.currency ?: "KZT",
                                            expiry = cardData.expiry
                                        )
                                    }
                                }
                            } else {
                                // Карты нет, создаем новую с ID с сервера
                                val existingCards = cardDao.getCardsByUserId(userId)
                                val card = Card(
                                    id = cardData.id,
                                    userId = userId,
                                    number = cardData.number,
                                    type = cardData.type,
                                    balance = cardData.balance,
                                    currency = cardData.currency ?: "KZT",
                                    expiry = cardData.expiry,
                                    isLinked = false,
                                    displayOrder = existingCards.size,
                                    isHidden = false,
                                    isDeleted = false
                                )
                                cardDao.insertCard(card)
                            }
                        }
                        
                        // Удаляем карты, которых больше нет на сервере (только если они не были привязаны локально)
                        // Используем все карты включая удаленные для проверки
                        allLocalCards.forEach { localCard ->
                            // Пропускаем уже удаленные карты
                            if (localCard.isDeleted) {
                                return@forEach
                            }
                            val normalizedLocalNumber = localCard.number.replace(" ", "").replace("-", "")
                            if (!serverCardNumbers.contains(normalizedLocalNumber) && !localCard.isLinked) {
                                // Карты нет на сервере и она не была привязана локально - удаляем
                                cardDao.deleteCard(localCard.id, userId)
                            }
                        }
                        
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Ошибка синхронизации карт"))
                    }
                } else {
                    Result.failure(Exception("Ошибка получения карт с сервера"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    
    private suspend fun fallbackToLocalUser(name: String, phone: String, password: String): Result<Long> {
        return try {
            val existing = userDao.getUserByPhone(phone)
            if (existing != null) {
                return Result.failure(Exception("Пользователь с таким номером телефона уже существует"))
            }

            val localUser = User(
                name = name,
                phone = phone,
                password = password
            )
            val newId = userDao.insertUser(localUser)
            // Room всегда возвращает rowId > 0 при успешной вставке
            Result.success(newId)
        } catch (e: Exception) {
            Result.failure(Exception("Не удалось сохранить пользователя: ${e.message}"))
        }
    }
}
