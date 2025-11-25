package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.f_bank.data.Card
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityCardsListBinding
import com.example.f_bank.ui.CardAdapter
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.utils.onFailure
import kotlinx.coroutines.launch

class CardsListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCardsListBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var cardAdapter: CardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)

        setupRecyclerView()
        setupClickListeners()
        loadCards()
    }

    private fun setupRecyclerView() {
        cardAdapter = CardAdapter(
            cards = emptyList(),
            showCreateButton = false,
            cardholderName = "",
            onCardClick = { card ->
                // Открываем детальную информацию о карте
                val intent = Intent(this, CardDetailsActivity::class.java)
                intent.putExtra("card_id", card.id)
                startActivity(intent)
            },
            onCreateCardClick = {
                // Не используется в этом экране
            }
        )
        binding.rvCards.layoutManager = LinearLayoutManager(this)
        binding.rvCards.adapter = cardAdapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAddCard.setOnClickListener {
            createNewCard()
        }
    }

    private fun loadCards() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.getCards(userId)
                    .onSuccess { cards ->
                        cardAdapter = CardAdapter(
                            cards = cards,
                            showCreateButton = false,
                            cardholderName = "",
                            onCardClick = { card ->
                                // Открываем детальную информацию о карте
                                val intent = Intent(this@CardsListActivity, CardDetailsActivity::class.java)
                                intent.putExtra("card_id", card.id)
                                startActivity(intent)
                            },
                            onCreateCardClick = {
                                // Не используется в этом экране
                            }
                        )
                        binding.rvCards.adapter = cardAdapter
                    }
                    .onFailure { error ->
                        Toast.makeText(this@CardsListActivity, "Ошибка загрузки карт: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun createNewCard() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            val token = securityPreferences.getAuthToken()
            if (userId != null && token != null) {
                // Проверяем количество карт перед созданием
                userRepository.getCards(userId)
                    .onSuccess { cards ->
                        if (cards.size >= 3) {
                            Toast.makeText(
                                this@CardsListActivity,
                                "Достигнут лимит карт (максимум 3 карты)",
                                Toast.LENGTH_LONG
                            ).show()
                            return@onSuccess
                        }
                        
                        // Показываем диалог выбора типа карты
                        showCardTypeSelectionDialog(userId, token)
                    }
                    .onFailure {
                        Toast.makeText(this@CardsListActivity, "Ошибка проверки карт", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this@CardsListActivity, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
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
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createCardWithType(userId: Long, token: String, cardType: String) {
        lifecycleScope.launch {
            userRepository.createCard(token, userId, cardType)
                .onSuccess { cardData ->
                    val cardTypeName = if (cardType == "credit") getString(R.string.credit_card) else getString(R.string.debit_card)
                    Toast.makeText(this@CardsListActivity, "$cardTypeName ${getString(R.string.card_created_successfully)}", Toast.LENGTH_SHORT).show()
                    // Перезагружаем список карт
                    loadCards()
                }
                .onFailure { error ->
                    Toast.makeText(this@CardsListActivity, "Ошибка создания карты: ${error.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        // Перезагружаем карты при возврате на экран
        loadCards()
    }
}

