package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.FavoriteContactEntity
import com.example.f_bank.databinding.ActivityAddFavoriteFromHistoryBinding
import com.example.f_bank.ui.RecentTransferAdapter
import com.example.f_bank.utils.PhoneNumberFormatter
import com.example.f_bank.utils.SecurityPreferences
import kotlinx.coroutines.launch

class AddFavoriteFromHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddFavoriteFromHistoryBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var favoriteContactDao: com.example.f_bank.data.FavoriteContactDao
    private lateinit var transactionDao: com.example.f_bank.data.TransactionDao
    private var userId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddFavoriteFromHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userId = securityPreferences.getCurrentUserId()
        
        val db = AppDatabase.getDatabase(this)
        favoriteContactDao = db.favoriteContactDao()
        transactionDao = db.transactionDao()

        setupClickListeners()
        loadRecentTransfers()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadRecentTransfers() {
        lifecycleScope.launch {
            try {
                userId?.let { uid ->
                    val transactions = transactionDao.getTransactionsByUserId(uid, 100)
                    
                    // Извлекаем уникальные номера телефонов из транзакций
                    // Ищем транзакции, которые содержат информацию о переводе по телефону
                    val phoneTransfers = transactions
                        .filter { 
                            it.title.contains("Перевод по номеру", ignoreCase = true) ||
                            it.title.contains("по номеру телефона", ignoreCase = true) ||
                            it.title.matches(Regex(".*\\+?7\\d{10}.*"))
                        }
                        .mapNotNull { transaction ->
                            // Пытаемся извлечь номер телефона из описания транзакции
                            // Ищем паттерны: +7XXXXXXXXXX, 7XXXXXXXXXX
                            val phoneRegex = Regex("""(\+?7\d{10})""")
                            val match = phoneRegex.find(transaction.title)
                            match?.value?.replace(Regex("[^0-9]"), "")?.let { digits ->
                                // Нормализуем номер
                                if (digits.length == 11 && digits.startsWith("7")) {
                                    digits
                                } else if (digits.length == 10) {
                                    "7$digits"
                                } else {
                                    null
                                }
                            }
                        }
                        .filterNotNull()
                        .distinct()
                        .take(20) // Берем последние 20 уникальных номеров

                    if (phoneTransfers.isEmpty()) {
                        binding.tvEmpty.visibility = android.view.View.VISIBLE
                        binding.rvRecentTransfers.visibility = android.view.View.GONE
                    } else {
                        binding.tvEmpty.visibility = android.view.View.GONE
                        binding.rvRecentTransfers.visibility = android.view.View.VISIBLE
                        
                        val adapter = RecentTransferAdapter(phoneTransfers) { phone ->
                            // Получаем имя пользователя по номеру телефона
                            getUserNameByPhone(phone) { name ->
                                addToFavorites(name ?: phone, phone)
                            }
                        }
                        binding.rvRecentTransfers.layoutManager = LinearLayoutManager(this@AddFavoriteFromHistoryActivity)
                        binding.rvRecentTransfers.adapter = adapter
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddFavoriteFromHistoryActivity, "Ошибка загрузки истории: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getUserNameByPhone(phone: String, callback: (String?) -> Unit) {
        lifecycleScope.launch {
            try {
                val userRepository = com.example.f_bank.data.UserRepository(this@AddFavoriteFromHistoryActivity)
                val result = userRepository.getUserByPhone(phone)
                result.onSuccess { name ->
                    callback(name)
                }.onFailure {
                    callback(null)
                }
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    private fun addToFavorites(name: String, phone: String) {
        lifecycleScope.launch {
            try {
                userId?.let { uid ->
                    // Проверяем, не добавлен ли уже этот контакт
                    val existing = favoriteContactDao.getFavoriteContactByPhone(uid, phone)
                    if (existing != null) {
                        Toast.makeText(this@AddFavoriteFromHistoryActivity, "Контакт уже в избранном", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val favoriteContact = FavoriteContactEntity(
                        userId = uid,
                        name = name,
                        phone = phone
                    )
                    favoriteContactDao.insertFavoriteContact(favoriteContact)
                    Toast.makeText(this@AddFavoriteFromHistoryActivity, "Контакт добавлен в избранное", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddFavoriteFromHistoryActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

