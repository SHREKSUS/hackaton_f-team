package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.R
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.databinding.ActivityRegisterStep1Binding
import com.example.f_bank.utils.ParticleSplashEffect
import com.example.f_bank.utils.PhoneNumberFormatter
import kotlinx.coroutines.launch

class RegisterStep1Activity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterStep1Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStep1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPhoneInputBehavior()

        binding.btnNext.setOnClickListener {
            ParticleSplashEffect.play(
                it,
                ContextCompat.getColor(this, R.color.button_primary_bg)
            )
            applyPhoneMaskIfNeeded()
            val phone = binding.etPhone.text.toString().trim()

            if (validatePhone(phone)) {
                val cleanedPhone = PhoneNumberFormatter.unformatPhone(phone)
                checkPhoneExists(cleanedPhone)
            }
        }

        binding.tvLoginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
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

    private fun checkPhoneExists(phone: String) {
        lifecycleScope.launch {
            try {
                // Проверяем локально в базе данных
                val userDao = AppDatabase.getDatabase(this@RegisterStep1Activity).userDao()
                val existingUser = userDao.getUserByPhone(phone)
                
                if (existingUser != null) {
                    // Пользователь с таким номером уже существует
                    binding.tilPhone.error = getString(R.string.error_user_exists)
                    Toast.makeText(
                        this@RegisterStep1Activity,
                        getString(R.string.error_user_exists),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Номера нет в локальной БД, переходим к следующему шагу
                    navigateToStep2(phone)
                }
            } catch (e: Exception) {
                // При ошибке проверки все равно переходим дальше
                // Сервер проверит при регистрации
                navigateToStep2(phone)
            }
        }
    }

    private fun navigateToStep2(phone: String) {
        val intent = Intent(this, RegisterStep2Activity::class.java)
        intent.putExtra("phone", phone)
        startActivity(intent)
    }
}

