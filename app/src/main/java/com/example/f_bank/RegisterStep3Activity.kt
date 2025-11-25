package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.UnderlineSpan
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.R
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityRegisterStep3Binding
import com.example.f_bank.utils.ParticleSplashEffect
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import kotlinx.coroutines.launch

class RegisterStep3Activity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterStep3Binding
    private lateinit var userRepository: UserRepository
    private var phone: String = ""
    private var lastName: String = ""
    private var firstName: String = ""
    private var middleName: String = ""
    private var inn: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStep3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)

        phone = intent.getStringExtra("phone") ?: ""
        lastName = intent.getStringExtra("lastName") ?: ""
        firstName = intent.getStringExtra("firstName") ?: ""
        middleName = intent.getStringExtra("middleName") ?: ""
        inn = intent.getStringExtra("inn") ?: ""

        if (phone.isEmpty() || lastName.isEmpty() || firstName.isEmpty() || inn.isEmpty()) {
            Toast.makeText(this, "Ошибка: данные не найдены", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        // Добавляем подчеркивание для ссылок
        val termsText = SpannableString("Просмотреть условия использования")
        termsText.setSpan(UnderlineSpan(), 0, termsText.length, 0)
        binding.tvTermsLink.text = termsText

        val dataProcessingText = SpannableString("Просмотреть политику конфиденциальности")
        dataProcessingText.setSpan(UnderlineSpan(), 0, dataProcessingText.length, 0)
        binding.tvDataProcessingLink.text = dataProcessingText

        binding.tvTermsLink.setOnClickListener {
            // TODO: Открыть условия использования (можно открыть WebView или браузер)
            Toast.makeText(this, "Условия использования", Toast.LENGTH_SHORT).show()
        }

        binding.tvDataProcessingLink.setOnClickListener {
            // TODO: Открыть политику конфиденциальности (можно открыть WebView или браузер)
            Toast.makeText(this, "Политика конфиденциальности", Toast.LENGTH_SHORT).show()
        }

        setupCheckboxListeners()
        
        // Инициализируем состояние кнопки при загрузке
        updateRegisterButtonState()

        binding.btnRegister.setOnClickListener {
            if (!binding.btnRegister.isEnabled) {
                // Кнопка отключена, показываем сообщение о том, что нужно заполнить все поля
                val password = binding.etPassword.text.toString()
                val confirmPassword = binding.etConfirmPassword.text.toString()
                
                if (password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(this, "Заполните все поля пароля", Toast.LENGTH_SHORT).show()
                } else if (!binding.cbTerms.isChecked || !binding.cbDataProcessing.isChecked) {
                    Toast.makeText(this, "Необходимо согласиться с условиями", Toast.LENGTH_SHORT).show()
                } else if (password != confirmPassword) {
                    Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()
                } else if (password.length < 6) {
                    Toast.makeText(this, "Пароль должен содержать минимум 6 символов", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }
            
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (validateInput(password, confirmPassword) && validateAgreements()) {
                ParticleSplashEffect.play(
                    it,
                    ContextCompat.getColor(this, R.color.button_primary_bg)
                )
                // Регистрируем пользователя после валидации пароля и принятия условий
                registerUser(phone, lastName, firstName, middleName, inn, password)
            }
        }
    }

    private fun setupCheckboxListeners() {
        binding.cbTerms.setOnCheckedChangeListener { _, _ ->
            updateRegisterButtonState()
        }

        binding.cbDataProcessing.setOnCheckedChangeListener { _, _ ->
            updateRegisterButtonState()
        }

        // Добавляем слушатели для полей пароля
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tilPassword.error = null
                updateRegisterButtonState()
            }
        })

        binding.etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tilConfirmPassword.error = null
                updateRegisterButtonState()
            }
        })
    }

    private fun updateRegisterButtonState() {
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val termsAccepted = binding.cbTerms.isChecked
        val dataProcessingAccepted = binding.cbDataProcessing.isChecked
        
        val isPasswordValid = password.isNotEmpty() && 
                              password.length >= 6 && 
                              confirmPassword.isNotEmpty() && 
                              password == confirmPassword
        
        binding.btnRegister.isEnabled = termsAccepted && dataProcessingAccepted && isPasswordValid
    }

    private fun validateInput(password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_password_empty)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = getString(R.string.error_confirm_password_empty)
            isValid = false
        } else if (password != confirmPassword) {
            binding.tilConfirmPassword.error = getString(R.string.error_password_mismatch)
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun validateAgreements(): Boolean {
        if (!binding.cbTerms.isChecked) {
            Toast.makeText(this, "Необходимо согласиться с условиями использования", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!binding.cbDataProcessing.isChecked) {
            Toast.makeText(this, "Необходимо согласиться на обработку данных", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun registerUser(phone: String, lastName: String, firstName: String, middleName: String, inn: String, password: String) {
        lifecycleScope.launch {
            try {
                // Объединяем ФИО в полное имя (отчество может быть пустым)
                val fullName = if (middleName.isNotEmpty()) {
                    "$lastName $firstName $middleName"
                } else {
                    "$lastName $firstName"
                }.trim()
                val result = userRepository.registerUser(fullName, phone, password)
                result.onSuccess { userId ->
                    val securityPrefs = com.example.f_bank.utils.SecurityPreferences(this@RegisterStep3Activity)
                    securityPrefs.saveCurrentUserId(userId)

                    val registerResponse = userRepository.getLastRegisterResponse()
                    registerResponse?.token?.let { token ->
                        securityPrefs.saveAuthToken(token)
                    }

                    Toast.makeText(
                        this@RegisterStep3Activity,
                        getString(R.string.registration_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToPinSetup()
                }.onFailure { exception ->
                    Toast.makeText(
                        this@RegisterStep3Activity,
                        exception.message ?: getString(R.string.error_registration_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    this@RegisterStep3Activity,
                    getString(R.string.error_registration_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun navigateToPinSetup() {
        val intent = Intent(this@RegisterStep3Activity, CreatePinActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

