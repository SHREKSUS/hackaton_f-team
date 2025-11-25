package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.databinding.ActivityLinkCardBinding
import com.example.f_bank.data.UserRepository
import com.example.f_bank.utils.PhoneNumberFormatter
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.utils.onFailure
import kotlinx.coroutines.launch

class LinkCardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLinkCardBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private var isLinkingByPhone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLinkCardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)

        setupClickListeners()
        setupCardNumberFormatter()
        setupExpiryFormatter()
        setupPhoneFormatter()
        setupLinkMethodToggle()
    }
    
    private fun setupLinkMethodToggle() {
        binding.toggleLinkMethod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isLinkingByPhone = checkedId == binding.btnLinkByPhone.id
                updateUIForLinkMethod()
            }
        }
        // Устанавливаем начальное состояние
        updateUIForLinkMethod()
    }
    
    private fun updateUIForLinkMethod() {
        if (isLinkingByPhone) {
            // Показываем поле для номера телефона
            binding.tilPhoneNumber.visibility = View.VISIBLE
            // Скрываем поля для данных карты
            binding.tilCardNumber.visibility = View.GONE
            binding.tilExpiry.visibility = View.GONE
            binding.tilCvv.visibility = View.GONE
            binding.tilCardholderName.visibility = View.GONE
            // Обновляем constraint кнопки
            val params = binding.btnLinkCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToBottom = binding.tilPhoneNumber.id
            binding.btnLinkCard.layoutParams = params
            // Обновляем текст кнопки
            binding.btnLinkCard.text = "Привязать по номеру телефона"
        } else {
            // Показываем поля для данных карты
            binding.tilCardNumber.visibility = View.VISIBLE
            binding.tilExpiry.visibility = View.VISIBLE
            binding.tilCvv.visibility = View.VISIBLE
            binding.tilCardholderName.visibility = View.VISIBLE
            // Скрываем поле для номера телефона
            binding.tilPhoneNumber.visibility = View.GONE
            // Обновляем constraint кнопки
            val params = binding.btnLinkCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToBottom = binding.tilCardholderName.id
            binding.btnLinkCard.layoutParams = params
            // Обновляем текст кнопки
            binding.btnLinkCard.text = "Привязать карту"
        }
    }
    
    private fun setupPhoneFormatter() {
        val phoneFormatter = PhoneNumberFormatter(binding.etPhoneNumber)
        binding.etPhoneNumber.addTextChangedListener(phoneFormatter)
    }

    private fun setupCardNumberFormatter() {
        binding.etCardNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().replace(" ", "")
                if (text.length <= 16) {
                    val formatted = formatCardNumber(text)
                    if (formatted != s.toString()) {
                        binding.etCardNumber.removeTextChangedListener(this)
                        binding.etCardNumber.setText(formatted)
                        binding.etCardNumber.setSelection(formatted.length)
                        binding.etCardNumber.addTextChangedListener(this)
                    }
                }
            }
        })
    }

    private fun setupExpiryFormatter() {
        binding.etExpiry.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().replace("/", "")
                if (text.length <= 4) {
                    val formatted = if (text.length >= 2) {
                        "${text.substring(0, 2)}/${text.substring(2)}"
                    } else {
                        text
                    }
                    if (formatted != s.toString()) {
                        binding.etExpiry.removeTextChangedListener(this)
                        binding.etExpiry.setText(formatted)
                        binding.etExpiry.setSelection(formatted.length)
                        binding.etExpiry.addTextChangedListener(this)
                    }
                }
            }
        })
    }

    private fun formatCardNumber(number: String): String {
        val cleaned = number.replace(" ", "")
        return when {
            cleaned.length <= 4 -> cleaned
            cleaned.length <= 8 -> "${cleaned.substring(0, 4)} ${cleaned.substring(4)}"
            cleaned.length <= 12 -> "${cleaned.substring(0, 4)} ${cleaned.substring(4, 8)} ${cleaned.substring(8)}"
            else -> "${cleaned.substring(0, 4)} ${cleaned.substring(4, 8)} ${cleaned.substring(8, 12)} ${cleaned.substring(12, 16)}"
        }
    }

    private fun setupClickListeners() {
        binding.btnLinkCard.setOnClickListener {
            linkCard()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun linkCard() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId == null) {
                Toast.makeText(
                    this@LinkCardActivity,
                    "Ошибка: ID пользователя не найден",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            
            // Проверяем количество карт перед привязкой
            userRepository.getCards(userId)
                .onSuccess { cards ->
                    if (cards.size >= 3) {
                        Toast.makeText(
                            this@LinkCardActivity,
                            "Достигнут лимит карт (максимум 3 карты)",
                            Toast.LENGTH_LONG
                        ).show()
                        return@onSuccess
                    }
                    
                    if (isLinkingByPhone) {
                        linkCardByPhone(userId)
                    } else {
                        linkCardByDetails(userId)
                    }
                }
                .onFailure {
                    Toast.makeText(
                        this@LinkCardActivity,
                        "Ошибка проверки карт",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    
    private fun linkCardByPhone(userId: Long) {
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()
        
        // Валидация номера телефона
        val cleanedPhone = PhoneNumberFormatter.unformatPhone(phoneNumber)
        if (cleanedPhone.length != 11 || !cleanedPhone.startsWith("7")) {
            binding.tilPhoneNumber.error = "Введите корректный номер телефона (7XXXXXXXXXX)"
            return
        }
        
        lifecycleScope.launch {
            val result = userRepository.linkCardByPhone(userId, cleanedPhone)
            result.onSuccess { cardData ->
                Toast.makeText(
                    this@LinkCardActivity,
                    "Карта успешно привязана по номеру телефона",
                    Toast.LENGTH_SHORT
                ).show()
                // Возвращаемся на главный экран
                val intent = Intent(this@LinkCardActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }.onFailure { exception ->
                Toast.makeText(
                    this@LinkCardActivity,
                    exception.message ?: "Ошибка при привязке карты по номеру телефона",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun linkCardByDetails(userId: Long) {
        val cardNumber = binding.etCardNumber.text.toString().replace(" ", "")
        val expiry = binding.etExpiry.text.toString()
        val cvv = binding.etCvv.text.toString()
        val cardholderName = binding.etCardholderName.text.toString().trim()

        // Валидация
        if (cardNumber.length != 16) {
            binding.etCardNumber.error = "Введите 16 цифр номера карты"
            return
        }

        if (expiry.length != 5 || !expiry.matches(Regex("\\d{2}/\\d{2}"))) {
            binding.etExpiry.error = "Введите срок действия (MM/YY)"
            return
        }

        if (cvv.length < 3 || cvv.length > 4) {
            binding.etCvv.error = "Введите CVV (3-4 цифры)"
            return
        }

        if (cardholderName.isEmpty()) {
            binding.etCardholderName.error = "Введите имя держателя карты"
            return
        }

        lifecycleScope.launch {
            val result = userRepository.linkCard(userId, cardNumber, expiry, cvv, cardholderName)
            result.onSuccess { cardData ->
                Toast.makeText(
                    this@LinkCardActivity,
                    "Карта успешно привязана",
                    Toast.LENGTH_SHORT
                ).show()
                // Возвращаемся на главный экран
                val intent = Intent(this@LinkCardActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }.onFailure { exception ->
                Toast.makeText(
                    this@LinkCardActivity,
                    exception.message ?: "Ошибка при привязке карты",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

