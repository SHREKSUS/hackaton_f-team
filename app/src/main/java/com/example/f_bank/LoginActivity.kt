package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.R
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityLoginBinding
import com.example.f_bank.utils.ParticleSplashEffect
import com.example.f_bank.utils.PhoneNumberFormatter
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.utils.onFailure
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var userRepository: UserRepository
    private lateinit var securityPreferences: SecurityPreferences
    private var isLoggingIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)
        securityPreferences = SecurityPreferences(this)

        setupPhoneInputBehavior()

        binding.btnLogin.setOnClickListener {
            if (isLoggingIn) return@setOnClickListener // Предотвращаем повторные вызовы
            
            ParticleSplashEffect.play(
                it,
                ContextCompat.getColor(this, R.color.button_primary_bg)
            )
            applyPhoneMaskIfNeeded()
            val emailOrPhone = binding.etEmailOrPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (validateInput(emailOrPhone, password)) {
                loginUser(emailOrPhone, password)
            }
        }

        binding.tvRegisterLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loginUser(phone: String, password: String) {
        if (isLoggingIn) return // Предотвращаем повторные вызовы
        
        isLoggingIn = true
        binding.btnLogin.isEnabled = false
        
        lifecycleScope.launch {
            try {
                // Удаляем форматирование перед отправкой - сервер сам определит, что это телефон
                val cleanedPhone = PhoneNumberFormatter.unformatPhone(phone)
                
                val result = userRepository.loginUser(cleanedPhone, password)
                android.util.Log.d("LoginActivity", "Login result: ${result.isSuccess}")
                result.onSuccess { user ->
                    android.util.Log.d("LoginActivity", "Login successful, user id: ${user.id}, name: ${user.name}")
                    // Сохраняем ID пользователя для работы с PIN
                    securityPreferences.saveCurrentUserId(user.id)
                    
                    // Сохраняем токен из ответа сервера (если есть)
                    val loginResponse = userRepository.getLastLoginResponse()
                    loginResponse?.token?.let { token ->
                        securityPreferences.saveAuthToken(token)
                        
                        // Синхронизируем карты с сервером для обновления балансов
                        userRepository.syncCardsFromServer(user.id, token)
                    }
                    
                    Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.login_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Всегда переходим на экран PIN (создание или ввод)
                    // CreatePinActivity сам определит, нужно ли создавать PIN или вводить его
                    val intent = Intent(this@LoginActivity, CreatePinActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }.onFailure { exception ->
                    android.util.Log.e("LoginActivity", "Login failed: ${exception.message}", exception)
                    isLoggingIn = false
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(
                        this@LoginActivity,
                        exception.message ?: getString(R.string.error_login_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                isLoggingIn = false
                binding.btnLogin.isEnabled = true
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.error_login_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        // Проверяем формат 7(XXX)XXX-XX-XX (российский формат)
        return PhoneNumberFormatter.isValidFormattedPhone(phone)
    }

    private fun validateInput(phone: String, password: String): Boolean {
        if (phone.isEmpty()) {
            binding.tilEmailOrPhone.error = getString(R.string.error_phone_empty)
            return false
        }
        
        if (!isValidPhoneNumber(phone)) {
            binding.tilEmailOrPhone.error = getString(R.string.error_phone_invalid)
            return false
        }
        
        if (password.isEmpty()) {
            binding.etPassword.error = getString(R.string.error_password_empty)
            return false
        }
        if (password.length < 6) {
            binding.etPassword.error = getString(R.string.error_password_short)
            return false
        }
        return true
    }

    private fun setupPhoneInputBehavior() {
        binding.etEmailOrPhone.setOnFocusChangeListener { _, hasFocus ->
            handlePhoneFieldFocusChange(hasFocus)
        }
    }

    private fun handlePhoneFieldFocusChange(hasFocus: Boolean) {
        val editText = binding.etEmailOrPhone
        val value = editText.text?.toString().orEmpty()
        if (value.isEmpty()) return

        if (hasFocus) {
            val digits = PhoneNumberFormatter.unformatPhone(value)
            if (digits != value) {
                editText.setText(digits)
                editText.setSelection(digits.length)
            } else {
                editText.setSelection(digits.length)
            }
        } else {
            applyPhoneMaskIfNeeded()
        }
    }

    private fun applyPhoneMaskIfNeeded() {
        val editText = binding.etEmailOrPhone
        val value = editText.text?.toString().orEmpty()
        if (value.isEmpty()) return
        val digits = PhoneNumberFormatter.unformatPhone(value)
        if (digits.length == 11) {
            val formatted = PhoneNumberFormatter.formatDigits(digits)
            if (formatted != value) {
                editText.setText(formatted)
                editText.setSelection(formatted.length)
            }
        } else {
            if (digits != value) {
                editText.setText(digits)
                editText.setSelection(digits.length)
            }
        }
    }
    
}
