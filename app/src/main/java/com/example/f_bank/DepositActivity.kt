package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.Card
import com.example.f_bank.data.TransactionEntity
import com.example.f_bank.data.TransactionType
import com.example.f_bank.databinding.ActivityDepositBinding
import com.example.f_bank.databinding.BottomSheetCardSelectionBinding
import com.example.f_bank.databinding.ItemCardSelectionBinding
import com.example.f_bank.utils.SecurityPreferences
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class DepositActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDepositBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var cardDao: com.example.f_bank.data.CardDao
    private lateinit var transactionDao: com.example.f_bank.data.TransactionDao
    private var cards: List<Card> = emptyList()
    private var selectedCard: Card? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDepositBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        val database = AppDatabase.getDatabase(this)
        cardDao = database.cardDao()
        transactionDao = database.transactionDao()

        setupClickListeners()
        setupAmountInput()
        loadCards()
        updateCardDisplay()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSelectCard.setOnClickListener {
            showCardSelectionBottomSheet()
        }

        binding.btnDeposit.setOnClickListener {
            performDeposit()
        }
    }

    private fun setupAmountInput() {
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tilAmount.error = null
            }
        })
    }

    private fun loadCards() {
        lifecycleScope.launch {
            try {
                val userId = securityPreferences.getCurrentUserId()
                if (userId != null) {
                    cards = cardDao.getCardsByUserId(userId)
                        .filter { !it.isHidden && !it.isBlocked }
                        .sortedBy { it.displayOrder }
                    
                    if (cards.isNotEmpty() && selectedCard == null) {
                        selectedCard = cards.first()
                        updateCardDisplay()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@DepositActivity, "Ошибка загрузки карт: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCardDisplay() {
        selectedCard?.let { card ->
            val lastFour = card.number.filter { it.isDigit() }.takeLast(4)
            val cardText = "•••• $lastFour"
            
            val format = NumberFormat.getNumberInstance(Locale.getDefault())
            format.maximumFractionDigits = 2
            format.minimumFractionDigits = 0
            val balanceFormatted = format.format(card.balance)
            
            binding.btnSelectCard.text = "$cardText • $balanceFormatted ₸"
        } ?: run {
            binding.btnSelectCard.text = "Выберите карту"
        }
    }

    private fun showCardSelectionBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetCardSelectionBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bottomSheetBinding.root)

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<CardSelectionViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CardSelectionViewHolder {
                val binding = ItemCardSelectionBinding.inflate(
                    android.view.LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                return CardSelectionViewHolder(binding)
            }

            override fun onBindViewHolder(holder: CardSelectionViewHolder, position: Int) {
                val card = cards[position]
                val lastFour = card.number.filter { it.isDigit() }.takeLast(4)
                val cardText = "•••• $lastFour"
                
                val format = NumberFormat.getNumberInstance(Locale.getDefault())
                format.maximumFractionDigits = 2
                format.minimumFractionDigits = 0
                val balanceFormatted = format.format(card.balance)
                
                holder.binding.tvCardNumber.text = cardText
                holder.binding.tvCardBalance.text = "$balanceFormatted ₸"
                
                holder.itemView.setOnClickListener {
                    selectedCard = card
                    updateCardDisplay()
                    bottomSheet.dismiss()
                }
            }

            override fun getItemCount() = cards.size
        }

        bottomSheetBinding.rvCards.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        bottomSheetBinding.rvCards.adapter = adapter

        bottomSheet.show()
    }

    private fun performDeposit() {
        val amountText = binding.etAmount.text.toString().trim()
        
        if (amountText.isEmpty()) {
            binding.tilAmount.error = "Введите сумму пополнения"
            return
        }

        val amount = try {
            amountText.replace(",", ".").toDouble()
        } catch (e: NumberFormatException) {
            binding.tilAmount.error = "Неверный формат суммы"
            return
        }

        if (amount <= 0) {
            binding.tilAmount.error = "Сумма должна быть больше нуля"
            return
        }

        if (amount > 1000000) {
            binding.tilAmount.error = "Максимальная сумма пополнения: 1 000 000 ₸"
            return
        }

        if (selectedCard == null) {
            Toast.makeText(this, "Выберите карту для пополнения", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val card = selectedCard!!
                val userId = securityPreferences.getCurrentUserId()
                
                if (userId == null) {
                    Toast.makeText(this@DepositActivity, "Ошибка: пользователь не найден", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Вызываем API для пополнения карты
                val userRepository = com.example.f_bank.data.UserRepository(this@DepositActivity)
                val result = userRepository.depositToCard(card.id, amount)
                
                result.onSuccess { depositResponse ->
                    // Создаем транзакцию локально
                    val transaction = TransactionEntity(
                        userId = userId,
                        cardId = card.id,
                        title = "Пополнение счета",
                        amount = amount,
                        type = TransactionType.DEPOSIT.name,
                        timestamp = System.currentTimeMillis(),
                        iconRes = R.drawable.ic_transfer_up
                    )
                    transactionDao.insertTransaction(transaction)

                    Toast.makeText(this@DepositActivity, "Счет успешно пополнен", Toast.LENGTH_SHORT).show()
                    
                    // Возвращаемся на главный экран с обновлением данных
                    val intent = Intent(this@DepositActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("refresh_cards", true)
                    intent.putExtra("refresh_transactions", true)
                    startActivity(intent)
                    finish()
                }.onFailure { exception ->
                    Toast.makeText(this@DepositActivity, "Ошибка пополнения: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DepositActivity, "Ошибка пополнения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class CardSelectionViewHolder(val binding: ItemCardSelectionBinding) : 
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)
}

