package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.databinding.ActivityCardSelectionBinding
import com.example.f_bank.data.UserRepository
import com.example.f_bank.api.model.CardData
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.R
import kotlinx.coroutines.launch

class CardSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCardSelectionBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Open new card
        binding.cardOpenNew.setOnClickListener {
            openNewCard()
        }

        // Link existing card
        binding.cardLinkExisting.setOnClickListener {
            linkExistingCard()
        }

        // Skip
        binding.tvSkip.setOnClickListener {
            skipCardSetup()
        }
    }

    private fun openNewCard() {
        lifecycleScope.launch {
            val token = securityPreferences.getAuthToken()
            val userId = securityPreferences.getCurrentUserId()
            
            if (token != null && userId != null) {
                // Проверяем количество карт перед созданием
                val cardsResult = userRepository.getCards(userId)
                if (cardsResult.isSuccess) {
                    val cards = cardsResult.getOrNull() ?: emptyList()
                    if (cards.size >= 3) {
                        Toast.makeText(
                            this@CardSelectionActivity,
                            "Достигнут лимит карт (максимум 3 карты)",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateToMain()
                        return@launch
                    }
                }
                
                // Показываем диалог выбора типа карты
                showCardTypeSelectionDialog(userId, token)
            } else {
                Toast.makeText(
                    this@CardSelectionActivity,
                    "Ошибка: токен авторизации или ID пользователя не найден",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToMain()
            }
        }
    }

    private fun showCardTypeSelectionDialog(userId: Long, token: String) {
        val options = arrayOf(
            getString(R.string.debit_card),
            getString(R.string.credit_card)
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_card_type))
            .setItems(options) { _, which ->
                val cardType = if (which == 0) "debit" else "credit"
                createCardWithType(userId, token, cardType)
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                navigateToMain()
            }
            .show()
    }

    private fun createCardWithType(userId: Long, token: String, cardType: String) {
        lifecycleScope.launch {
            val result = userRepository.createCard(token, userId, cardType)
            if (result.isSuccess) {
                val card = result.getOrNull()
                val cardTypeName = if (cardType == "credit") getString(R.string.credit_card) else getString(R.string.debit_card)
                Toast.makeText(
                    this@CardSelectionActivity,
                    "$cardTypeName ${getString(R.string.card_created_successfully)}",
                    Toast.LENGTH_SHORT
                ).show()
                // Небольшая задержка для завершения сохранения в БД
                kotlinx.coroutines.delay(100)
                navigateToMain()
            } else {
                val exception = result.exceptionOrNull()
                Toast.makeText(
                    this@CardSelectionActivity,
                    exception?.message ?: "Ошибка при создании карты",
                    Toast.LENGTH_SHORT
                ).show()
                // При ошибке все равно переходим на главный экран
                navigateToMain()
            }
        }
    }

    private fun linkExistingCard() {
        val intent = Intent(this, LinkCardActivity::class.java)
        startActivity(intent)
    }

    private fun skipCardSetup() {
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

