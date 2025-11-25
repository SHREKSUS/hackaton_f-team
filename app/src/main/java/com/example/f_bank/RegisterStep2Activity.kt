package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.f_bank.R
import com.example.f_bank.databinding.ActivityRegisterStep2Binding
import com.example.f_bank.utils.ParticleSplashEffect

class RegisterStep2Activity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterStep2Binding
    private var phone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStep2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        phone = intent.getStringExtra("phone") ?: ""

        if (phone.isEmpty()) {
            Toast.makeText(this, "Ошибка: номер телефона не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnNext.setOnClickListener {
            ParticleSplashEffect.play(
                it,
                ContextCompat.getColor(this, R.color.button_primary_bg)
            )
            val lastName = binding.etLastName.text.toString().trim()
            val firstName = binding.etFirstName.text.toString().trim()
            val middleName = binding.etMiddleName.text.toString().trim()
            val inn = binding.etInn.text.toString().trim()

            if (validateInput(lastName, firstName, middleName, inn)) {
                navigateToStep3(phone, lastName, firstName, middleName, inn)
            }
        }
    }

    private fun validateInput(lastName: String, firstName: String, middleName: String, inn: String): Boolean {
        var isValid = true

        if (lastName.isEmpty()) {
            binding.tilLastName.error = "Введите фамилию"
            isValid = false
        } else {
            binding.tilLastName.error = null
        }

        if (firstName.isEmpty()) {
            binding.tilFirstName.error = "Введите имя"
            isValid = false
        } else {
            binding.tilFirstName.error = null
        }

        // Отчество необязательно - убираем проверку
        binding.tilMiddleName.error = null

        if (inn.isEmpty()) {
            binding.tilInn.error = "Введите ИНН"
            isValid = false
        } else if (!isValidInn(inn)) {
            binding.tilInn.error = "ИНН должен содержать 12 цифр"
            isValid = false
        } else {
            binding.tilInn.error = null
        }

        return isValid
    }

    private fun isValidInn(inn: String): Boolean {
        // ИНН должен содержать 12 цифр
        return inn.length == 12 && inn.all { it.isDigit() }
    }

    private fun navigateToStep3(phone: String, lastName: String, firstName: String, middleName: String, inn: String) {
        val intent = Intent(this, RegisterStep3Activity::class.java)
        intent.putExtra("phone", phone)
        intent.putExtra("lastName", lastName)
        intent.putExtra("firstName", firstName)
        intent.putExtra("middleName", middleName)
        intent.putExtra("inn", inn)
        startActivity(intent)
    }
}

