package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.R
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityForgotPasswordBinding
import com.example.f_bank.utils.ParticleSplashEffect
import com.example.f_bank.utils.PhoneNumberFormatter
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var userRepository: UserRepository
    private lateinit var securityPreferences: SecurityPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)
        securityPreferences = SecurityPreferences(this)

        setupPhoneInputBehavior()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnResetPassword.setOnClickListener {
            ParticleSplashEffect.play(
                it,
                ContextCompat.getColor(this, R.color.button_primary_bg)
            )
            applyPhoneMaskIfNeeded()
            val phone = binding.etPhone.text.toString().trim()

            if (validatePhone(phone)) {
                val cleanedPhone = PhoneNumberFormatter.unformatPhone(phone)
                resetPassword(cleanedPhone)
            }
        }
    }

    private fun validatePhone(phone: String): Boolean {
        if (phone.isEmpty()) {
            binding.tilPhone.error = getString(R.string.error_phone_empty)
            return false
        }

        if (!PhoneNumberFormatter.isValidFormattedPhone(phone)) {
            binding.tilPhone.error = getString(R.string.error_phone_invalid)
            return false
        }

        binding.tilPhone.error = null
        return true
    }

    private fun setupPhoneInputBehavior() {
        binding.etPhone.setOnFocusChangeListener { _, hasFocus ->
            handlePhoneFieldFocusChange(hasFocus)
        }
    }

    private fun handlePhoneFieldFocusChange(hasFocus: Boolean) {
        val editText = binding.etPhone
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
        val editText = binding.etPhone
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

    private fun resetPassword(phone: String) {
        lifecycleScope.launch {
            try {
                // Проверяем, существует ли пользователь с таким номером
                val userDao = AppDatabase.getDatabase(this@ForgotPasswordActivity).userDao()
                val user = userDao.getUserByPhone(phone)

                if (user == null) {
                    binding.tilPhone.error = "Пользователь с таким номером не найден"
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Пользователь с таким номером не найден",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Здесь должна быть логика восстановления пароля через API
                // Пока показываем сообщение об успехе
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Инструкции по восстановлению пароля отправлены на номер $phone",
                    Toast.LENGTH_LONG
                ).show()

                // Переходим на экран входа
                val intent = Intent(this@ForgotPasswordActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

