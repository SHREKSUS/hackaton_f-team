package com.example.f_bank

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.api.RetrofitClient
import com.example.f_bank.api.model.SavePinRequest
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.databinding.ActivityCreatePinBinding
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.utils.onFailure
import com.example.f_bank.widget.PinIndicatorView
import kotlinx.coroutines.launch

class CreatePinActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreatePinBinding
    private lateinit var securityPreferences: SecurityPreferences
    private var biometricPrompt: BiometricPrompt? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null
    private var biometricAvailable = false

    private var currentPin = StringBuilder()
    private var confirmPin = StringBuilder()
    private var isConfirming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        setupBiometrics()
        setupListeners()
        setupKeypad()
        loadUserName()
        updateUiState()
        setupLogoutButton()
        setupForgotPassword()
    }

    private fun loadUserName() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                val userDao = AppDatabase.getDatabase(this@CreatePinActivity).userDao()
                val user = userDao.getUserById(userId)
                if (user != null) {
                    // Форматируем ФИО как "Фамилия И."
                    val nameParts = user.name.split(" ")
                    val displayName = when {
                        nameParts.size >= 2 -> {
                            // Фамилия Имя (Отчество) -> Фамилия И.
                            "${nameParts[0]} ${nameParts[1].firstOrNull()?.uppercaseChar()}."
                        }
                        nameParts.isNotEmpty() -> nameParts[0]
                        else -> user.name
                    }
                    binding.tvUserName.text = displayName
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnEnableFingerprint.setOnClickListener { showBiometricPrompt() }
    }

    private fun setupKeypad() {
        val keys = listOf(
            binding.btnKey0, binding.btnKey1, binding.btnKey2, binding.btnKey3,
            binding.btnKey4, binding.btnKey5, binding.btnKey6,
            binding.btnKey7, binding.btnKey8, binding.btnKey9
        )

        keys.forEachIndexed { index, button ->
            button.setOnClickListener {
                onKeypadClick(index.toString())
            }
        }

        // Кнопка отпечатка пальца
        binding.btnFingerprint.setOnClickListener {
            if (securityPreferences.isPinSet() && biometricAvailable) {
                showBiometricPrompt()
            } else if (!securityPreferences.isPinSet()) {
                Toast.makeText(this, getString(R.string.continue_without_pin), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.fingerprint_unavailable), Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка backspace
        binding.btnBackspace.setOnClickListener {
            handleBackspace()
        }
    }

    private fun onKeypadClick(digit: String) {
        val pinSaved = securityPreferences.isPinSet()
        
        if (pinSaved) {
            // Режим ввода PIN для входа - только один ввод, без подтверждения
            if (currentPin.length < 4) {
                currentPin.append(digit)
                binding.pinIndicator1.setPinLength(currentPin.length)
                
                if (currentPin.length == 4) {
                    // Сразу проверяем PIN без подтверждения
                    handlePinVerificationSingle()
                }
            }
        } else {
            // Режим создания PIN (PIN еще не установлен)
            // Проверяем еще раз, что PIN не был установлен между вводом
            if (securityPreferences.isPinSet()) {
                // PIN был установлен - переходим к режиму входа
                handlePinVerificationSingle()
                return
            }
            
            if (isConfirming) {
                if (confirmPin.length < 4) {
                    confirmPin.append(digit)
                    binding.pinIndicator2.setPinLength(confirmPin.length)
                    
                    if (confirmPin.length == 4) {
                        // Проверяем еще раз перед сохранением
                        if (!securityPreferences.isPinSet()) {
                            handlePinConfirmation()
                        } else {
                            // PIN был установлен - переходим к проверке
                            handlePinVerificationSingle()
                        }
                    }
                }
            } else {
                if (currentPin.length < 4) {
                    currentPin.append(digit)
                    binding.pinIndicator1.setPinLength(currentPin.length)
                    
                    if (currentPin.length == 4) {
                        // Проверяем, что PIN все еще не установлен
                        if (!securityPreferences.isPinSet()) {
                            startConfirmation()
                        } else {
                            // PIN был установлен - переходим к проверке
                            handlePinVerificationSingle()
                        }
                    }
                }
            }
        }
    }
    
    private fun handlePinVerificationSingle() {
        val enteredPin = currentPin.toString()
        
        // Проверяем правильность PIN (сначала локально для быстрой проверки)
        if (securityPreferences.verifyPinLocally(enteredPin)) {
            // PIN верный локально - сохраняем для шифрования карт
            securityPreferences.savePinForEncryption(enteredPin)
            
            // Проверяем на сервере
            binding.pinIndicator1.setState(PinIndicatorView.PinState.Success)
            lifecycleScope.launch {
                val userId = securityPreferences.getCurrentUserId()
                if (userId != null) {
                    verifyPinOnServer(userId, enteredPin)
                } else {
                    // Если userId не найден, используем только локальную проверку
                    navigateToMain()
                }
            }
        } else {
            // PIN неверный локально, но проверяем на сервере на всякий случай
            lifecycleScope.launch {
                val userId = securityPreferences.getCurrentUserId()
                if (userId != null) {
                    verifyPinOnServer(userId, enteredPin)
                } else {
                    // PIN неверный
                    binding.pinIndicator1.setState(PinIndicatorView.PinState.Error)
                    Toast.makeText(this@CreatePinActivity, "Неверный PIN-код", Toast.LENGTH_SHORT).show()
                    resetPinAfterError()
                }
            }
        }
    }
    
    private fun handlePinVerification() {
        val enteredPin = currentPin.toString()
        val confirmedPin = confirmPin.toString()
        
        // Проверяем, что оба PIN совпадают (используется только при создании PIN)
        if (enteredPin == confirmedPin) {
            // Проверяем правильность PIN (сначала локально для быстрой проверки)
            if (securityPreferences.verifyPinLocally(enteredPin)) {
                // PIN верный локально, проверяем на сервере
                lifecycleScope.launch {
                    val userId = securityPreferences.getCurrentUserId()
                    if (userId != null) {
                        verifyPinOnServer(userId, enteredPin)
                    } else {
                        // Если userId не найден, используем только локальную проверку
                        binding.pinIndicator2.setState(PinIndicatorView.PinState.Success)
                        navigateToMain()
                    }
                }
            } else {
                // PIN неверный локально, но проверяем на сервере на всякий случай
                lifecycleScope.launch {
                    val userId = securityPreferences.getCurrentUserId()
                    if (userId != null) {
                        verifyPinOnServer(userId, enteredPin)
                    } else {
                        // PIN неверный
                        binding.pinIndicator2.setState(PinIndicatorView.PinState.Error)
                        Toast.makeText(this@CreatePinActivity, "Неверный PIN-код", Toast.LENGTH_SHORT).show()
                        resetPinAfterError()
                    }
                }
            }
        } else {
            // PIN не совпадают
            binding.pinIndicator2.setState(PinIndicatorView.PinState.Error)
            Toast.makeText(this, "PIN-коды не совпадают", Toast.LENGTH_SHORT).show()
            resetConfirmation()
        }
    }

    private fun startConfirmation() {
        // Проверяем, что это режим создания PIN, а не входа
        val pinSaved = securityPreferences.isPinSet()
        if (pinSaved) {
            // При входе не нужно подтверждение - сразу проверяем
            handlePinVerificationSingle()
            return
        }
        
        isConfirming = true
        binding.tvSecondInstruction.isVisible = true
        binding.pinIndicator2.isVisible = true
        
        // При создании показываем инструкцию для создания
        binding.tvInstruction.text = getString(R.string.set_pin_instruction)
    }

    private fun handlePinConfirmation() {
        val enteredPin = currentPin.toString()
        val confirmedPin = confirmPin.toString()
        
        // Проверяем, что это режим создания PIN, а не входа
        val pinSaved = securityPreferences.isPinSet()
        if (pinSaved) {
            // Если PIN уже установлен, это не должно происходить - переходим к проверке
            handlePinVerificationSingle()
            return
        }
        
        if (enteredPin == confirmedPin && enteredPin.length == 4) {
            // PIN совпадают и имеют правильную длину
            binding.pinIndicator2.setState(PinIndicatorView.PinState.Success)
            
            // Сохраняем PIN на сервере и локально (только при создании)
            lifecycleScope.launch {
                val userId = securityPreferences.getCurrentUserId()
                if (userId != null) {
                    savePinToServer(userId, enteredPin)
                } else {
                    // Если userId не найден, сохраняем только локально (fallback)
                    securityPreferences.savePinLocally(enteredPin)
                    Toast.makeText(
                        this@CreatePinActivity,
                        "PIN сохранен локально. Подключитесь к серверу для синхронизации.",
                        Toast.LENGTH_SHORT
                    ).show()
                    checkCardsAndNavigate()
                }
            }
        } else {
            // PIN не совпадают
            binding.pinIndicator2.setState(PinIndicatorView.PinState.Error)
            Toast.makeText(this, getString(R.string.error_pin_mismatch), Toast.LENGTH_SHORT).show()
            
            // Сбрасываем подтверждение
            resetConfirmation()
        }
    }
    
    private suspend fun savePinToServer(userId: Long, pin: String) {
        // Проверяем, что PIN еще не установлен (защита от повторного сохранения)
        if (securityPreferences.isPinSet()) {
            // PIN уже установлен - не сохраняем повторно, просто переходим дальше
            checkCardsAndNavigate()
            return
        }
        
        try {
            // Сначала сохраняем локально для гарантии
            securityPreferences.savePinLocally(pin)
            // Сохраняем PIN для шифрования карт
            securityPreferences.savePinForEncryption(pin)
            
            val request = SavePinRequest(userId = userId, pin = pin)
            val response = RetrofitClient.apiService.savePin(request)
            
            if (response.isSuccessful && response.body() != null) {
                val pinResponse = response.body()!!
                if (pinResponse.success) {
                    // PIN успешно сохранен на сервере, локально уже сохранен
                    Toast.makeText(
                        this@CreatePinActivity,
                        pinResponse.message ?: "PIN успешно сохранен",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Проверяем наличие карты после создания PIN
                    checkCardsAndNavigate()
                } else {
                    // Сервер вернул ошибку, но локально уже сохранен
                    Toast.makeText(
                        this@CreatePinActivity,
                        pinResponse.message ?: "Ошибка сохранения PIN на сервере. PIN сохранен локально.",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Проверяем наличие карты
                    checkCardsAndNavigate()
                }
            } else {
                // HTTP ошибка, локально уже сохранен
                val errorBody = response.errorBody()?.string() ?: "Ошибка подключения к серверу"
                Toast.makeText(
                    this@CreatePinActivity,
                    "$errorBody. PIN сохранен локально.",
                    Toast.LENGTH_SHORT
                ).show()
                // При ошибке переходим на главный экран
                checkCardsAndNavigate()
            }
        } catch (e: Exception) {
            // Ошибка сети, локально уже сохранен
            Toast.makeText(
                this@CreatePinActivity,
                "Ошибка подключения к серверу: ${e.message}. PIN сохранен локально.",
                Toast.LENGTH_SHORT
            ).show()
            // При ошибке переходим на главный экран
            checkCardsAndNavigate()
        }
    }
    
    private fun checkCardsAndNavigate() {
        lifecycleScope.launch {
            val token = securityPreferences.getAuthToken()
            val userId = securityPreferences.getCurrentUserId()
            if (token != null && userId != null) {
                val userRepository = com.example.f_bank.data.UserRepository(this@CreatePinActivity)
                
                // Синхронизируем карты с сервером для обновления балансов
                userRepository.syncCardsFromServer(userId, token)
                
                // Проверяем, является ли это первым входом после регистрации
                val isFirstLoginAfterRegistration = securityPreferences.isFirstLoginAfterRegistration()
                
                val hasCards = userRepository.checkCards(token)
                hasCards.onSuccess { hasCardsResult ->
                    if (hasCardsResult) {
                        // Есть карты, переходим на главный экран
                        // Сбрасываем флаг первого входа, если он был установлен
                        if (isFirstLoginAfterRegistration) {
                            securityPreferences.setFirstLoginAfterRegistration(false)
                        }
                        navigateToMain()
                    } else {
                        // Нет карт - показываем экран выбора карты только при первом входе после регистрации
                        if (isFirstLoginAfterRegistration) {
                            // Сбрасываем флаг первого входа
                            securityPreferences.setFirstLoginAfterRegistration(false)
                            val intent = Intent(this@CreatePinActivity, CardSelectionActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            // Не первый вход - просто переходим на главный экран
                            navigateToMain()
                        }
                    }
                }.onFailure {
                    // При ошибке переходим на главный экран
                    // Сбрасываем флаг первого входа, если он был установлен
                    if (isFirstLoginAfterRegistration) {
                        securityPreferences.setFirstLoginAfterRegistration(false)
                    }
                    navigateToMain()
                }
            } else {
                // Нет токена, переходим на главный экран
                navigateToMain()
            }
        }
    }

    private suspend fun verifyPinOnServer(userId: Long, pin: String) {
        val pinSaved = securityPreferences.isPinSet()
        val indicator = if (pinSaved) binding.pinIndicator1 else binding.pinIndicator2
        
        try {
            val request = com.example.f_bank.api.model.VerifyPinRequest(userId = userId, pin = pin)
            val response = RetrofitClient.apiService.verifyPin(request)
            
            if (response.isSuccessful && response.body() != null) {
                val pinResponse = response.body()!!
                if (pinResponse.success) {
                    // PIN верный
                    indicator.setState(PinIndicatorView.PinState.Success)
                    // Проверяем наличие карты после успешного ввода PIN
                    checkCardsAndNavigate()
                } else {
                    // PIN неверный
                    indicator.setState(PinIndicatorView.PinState.Error)
                    Toast.makeText(
                        this@CreatePinActivity,
                        pinResponse.message ?: "Неверный PIN-код",
                        Toast.LENGTH_SHORT
                    ).show()
                    resetPinAfterError()
                }
            } else {
                // HTTP ошибка, используем локальную проверку как fallback
                if (securityPreferences.verifyPinLocally(pin)) {
                    indicator.setState(PinIndicatorView.PinState.Success)
                    Toast.makeText(
                        this@CreatePinActivity,
                        "Ошибка подключения к серверу. Используется локальная проверка.",
                        Toast.LENGTH_SHORT
                    ).show()
                    checkCardsAndNavigate()
                } else {
                    indicator.setState(PinIndicatorView.PinState.Error)
                    Toast.makeText(this@CreatePinActivity, "Неверный PIN-код", Toast.LENGTH_SHORT).show()
                    resetPinAfterError()
                }
            }
        } catch (e: Exception) {
            // Ошибка сети, используем локальную проверку как fallback
            if (securityPreferences.verifyPinLocally(pin)) {
                indicator.setState(PinIndicatorView.PinState.Success)
                Toast.makeText(
                    this@CreatePinActivity,
                    "Ошибка подключения к серверу. Используется локальная проверка.",
                    Toast.LENGTH_SHORT
                ).show()
                // При ошибке переходим на главный экран
                checkCardsAndNavigate()
            } else {
                indicator.setState(PinIndicatorView.PinState.Error)
                Toast.makeText(this@CreatePinActivity, "Неверный PIN-код", Toast.LENGTH_SHORT).show()
                resetPinAfterError()
            }
        }
    }
    
    private fun resetPinAfterError() {
        binding.pinIndicator1.postDelayed({
            currentPin.clear()
            confirmPin.clear()
            binding.pinIndicator1.setPinLength(0)
            binding.pinIndicator2.setPinLength(0)
            binding.pinIndicator1.setState(PinIndicatorView.PinState.Normal)
            binding.pinIndicator2.setState(PinIndicatorView.PinState.Normal)
            isConfirming = false
            binding.tvSecondInstruction.isVisible = false
            binding.pinIndicator2.isVisible = false
        }, 1000)
    }

    private fun resetConfirmation() {
        confirmPin.clear()
        binding.pinIndicator2.setPinLength(0)
        binding.pinIndicator2.setState(PinIndicatorView.PinState.Normal)
        // Не сбрасываем isConfirming, чтобы пользователь мог повторить ввод
    }

    private fun handleCancel() {
        if (isConfirming) {
            resetConfirmation()
            isConfirming = false
            binding.tvSecondInstruction.isVisible = false
            binding.pinIndicator2.isVisible = false
        } else {
            // Сбрасываем весь PIN
            currentPin.clear()
            binding.pinIndicator1.setPinLength(0)
        }
    }

    private fun handleBackspace() {
        val pinSaved = securityPreferences.isPinSet()
        
        if (pinSaved) {
            // Режим ввода PIN для входа
            if (isConfirming) {
                if (confirmPin.isNotEmpty()) {
                    confirmPin.deleteCharAt(confirmPin.length - 1)
                    binding.pinIndicator2.setPinLength(confirmPin.length)
                    binding.pinIndicator2.setState(PinIndicatorView.PinState.Normal)
                }
            } else {
                if (currentPin.isNotEmpty()) {
                    currentPin.deleteCharAt(currentPin.length - 1)
                    binding.pinIndicator1.setPinLength(currentPin.length)
                    binding.pinIndicator1.setState(PinIndicatorView.PinState.Normal)
                }
            }
        } else {
            // Режим создания PIN
            if (isConfirming) {
                if (confirmPin.isNotEmpty()) {
                    confirmPin.deleteCharAt(confirmPin.length - 1)
                    binding.pinIndicator2.setPinLength(confirmPin.length)
                    binding.pinIndicator2.setState(PinIndicatorView.PinState.Normal)
                }
            } else {
                if (currentPin.isNotEmpty()) {
                    currentPin.deleteCharAt(currentPin.length - 1)
                    binding.pinIndicator1.setPinLength(currentPin.length)
                    binding.pinIndicator1.setState(PinIndicatorView.PinState.Normal)
                }
            }
        }
    }

    private fun disableKeypad() {
        val keys = listOf(
            binding.btnKey0, binding.btnKey1, binding.btnKey2, binding.btnKey3,
            binding.btnKey4, binding.btnKey5, binding.btnKey6,
            binding.btnKey7, binding.btnKey8, binding.btnKey9
        )
        keys.forEach { it.isEnabled = false }
        binding.btnBackspace.isEnabled = false
        // Кнопка отпечатка остается активной
    }

    private fun enableKeypad() {
        val keys = listOf(
            binding.btnKey0, binding.btnKey1, binding.btnKey2, binding.btnKey3,
            binding.btnKey4, binding.btnKey5, binding.btnKey6,
            binding.btnKey7, binding.btnKey8, binding.btnKey9
        )
        keys.forEach { it.isEnabled = true }
        binding.btnBackspace.isEnabled = true
    }

    private fun handleContinue() {
        if (!securityPreferences.isPinSet()) {
            Toast.makeText(this, getString(R.string.continue_without_pin), Toast.LENGTH_SHORT).show()
            return
        }
        navigateToMain()
    }

    private fun setupBiometrics() {
        val manager = BiometricManager.from(this)
        biometricAvailable = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!biometricAvailable) return

        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                securityPreferences.setFingerprintEnabled(true)
                Toast.makeText(this@CreatePinActivity, getString(R.string.fingerprint_enabled), Toast.LENGTH_SHORT).show()
                navigateToMain()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@CreatePinActivity, errString, Toast.LENGTH_SHORT).show()
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.fingerprint_prompt_title))
            .setSubtitle(getString(R.string.fingerprint_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.cancel))
            .build()
    }

    private fun showBiometricPrompt() {
        if (!biometricAvailable || !securityPreferences.isPinSet()) {
            Toast.makeText(this, getString(R.string.fingerprint_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        promptInfo?.let { biometricPrompt?.authenticate(it) }
    }

    private fun updateUiState() {
        val pinSaved = securityPreferences.isPinSet()
        if (pinSaved) {
            binding.tvInstruction.text = getString(R.string.enter_pin_instruction)
            binding.pinIndicator1.setPinLength(0) // Сбрасываем для ввода
            binding.pinIndicator1.setState(PinIndicatorView.PinState.Normal)
            enableKeypad()
            
            // Кнопка отпечатка всегда видна
            binding.btnFingerprint.isEnabled = biometricAvailable
            
            // Показываем кнопку выхода в режиме входа
            binding.btnLogout.isVisible = true
            
            // Показываем ссылку "Забыли пароль" в режиме входа
            binding.tvForgotPassword.isVisible = true
            
            // Скрываем второй индикатор до начала ввода
            binding.tvSecondInstruction.isVisible = false
            binding.pinIndicator2.isVisible = false
            binding.fingerprintSection.isVisible = false
            binding.btnContinue.isVisible = false
        } else {
            binding.tvInstruction.text = getString(R.string.set_pin_instruction)
            enableKeypad()
            // Кнопка отпечатка всегда видна, но отключена до сохранения PIN
            binding.btnFingerprint.isEnabled = false
            // Скрываем кнопку выхода при создании PIN
            binding.btnLogout.isVisible = false
            // Скрываем ссылку "Забыли пароль" при создании PIN
            binding.tvForgotPassword.isVisible = false
            // Скрываем дополнительные элементы при создании PIN
            binding.fingerprintSection.isVisible = false
            binding.btnContinue.isVisible = false
            binding.tvSecondInstruction.isVisible = false
            binding.pinIndicator2.isVisible = false
        }
    }
    
    private fun setupForgotPassword() {
        binding.tvForgotPassword.setOnClickListener {
            // Переходим сразу на экран входа
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun setupLogoutButton() {
        // Показываем кнопку выхода только если PIN уже установлен (режим входа)
        val pinSaved = securityPreferences.isPinSet()
        binding.btnLogout.isVisible = pinSaved
        
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }
    
    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_confirmation))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun performLogout() {
        // Очищаем все сохраненные данные
        securityPreferences.clearAuthToken()
        securityPreferences.clearPinForEncryption()
        
        // Очищаем PIN и userId локально
        val prefs = getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("pin_hash")
            .remove("current_user_id")
            .remove("auth_token")
            .apply()
        
        // Переходим на экран приветствия
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
