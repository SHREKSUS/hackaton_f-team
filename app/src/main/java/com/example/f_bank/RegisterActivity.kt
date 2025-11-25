package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.R
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityRegisterBinding
import com.example.f_bank.utils.ParticleSplashEffect
import com.example.f_bank.utils.PhoneNumberFormatter
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.utils.onFailure
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)

        setupPhoneInputBehavior()

        binding.btnRegister.setOnClickListener {
            ParticleSplashEffect.play(
                it,
                ContextCompat.getColor(this, R.color.button_primary_bg)
            )
            applyPhoneMaskIfNeeded()
            val name = binding.etName.text.toString().trim()
            val emailOrPhone = binding.etEmailOrPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (validateInput(name, emailOrPhone, password, confirmPassword)) {
                // Удаляем форматирование перед отправкой (оставляем только цифры)
                val cleanedPhone = PhoneNumberFormatter.unformatPhone(emailOrPhone)
                registerUserByPhone(name, cleanedPhone, password)
            }
        }

        binding.tvLoginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }


    private fun registerUserByPhone(name: String, phone: String, password: String) {
        lifecycleScope.launch {
            try {
                // Используем телефон напрямую - сервер определит, что это телефон
                val result = userRepository.registerUser(name, phone, password)
                result.onSuccess { userId ->
                    // Сохраняем ID пользователя для отображения имени на экране PIN
                    val securityPrefs = com.example.f_bank.utils.SecurityPreferences(this@RegisterActivity)
                    securityPrefs.saveCurrentUserId(userId)
                    
                    // Сохраняем токен из ответа регистрации (если есть)
                    val registerResponse = userRepository.getLastRegisterResponse()
                    registerResponse?.token?.let { token ->
                        securityPrefs.saveAuthToken(token)
                    }
                    
                    // Устанавливаем флаг первого входа после регистрации
                    securityPrefs.setFirstLoginAfterRegistration(true)
                    
                    Toast.makeText(
                        this@RegisterActivity,
                        getString(R.string.registration_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToPinSetup()
                }.onFailure { exception ->
                    Toast.makeText(
                        this@RegisterActivity,
                        exception.message ?: getString(R.string.error_registration_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Игнорируем отмену корутины (например, при нажатии назад)
                throw e // Пробрасываем дальше для правильной обработки
            } catch (e: Exception) {
                Toast.makeText(
                    this@RegisterActivity,
                    getString(R.string.error_registration_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun validateInput(name: String, phone: String, password: String, confirmPassword: String): Boolean {
        if (name.isEmpty()) {
            binding.etName.error = getString(R.string.error_name_empty)
            return false
        }
        
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
        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = getString(R.string.error_confirm_password_empty)
            return false
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = getString(R.string.error_password_mismatch)
            return false
        }
        return true
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        // Проверяем формат 7(XXX)XXX-XX-XX (российский формат)
        return PhoneNumberFormatter.isValidFormattedPhone(phone)
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

    private fun navigateToPinSetup() {
        val intent = Intent(this@RegisterActivity, CreatePinActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}