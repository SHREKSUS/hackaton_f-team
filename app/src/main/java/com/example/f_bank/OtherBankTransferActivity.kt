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
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityOtherBankTransferBinding
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class OtherBankTransferActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOtherBankTransferBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var cardDao: com.example.f_bank.data.CardDao
    private lateinit var transactionDao: com.example.f_bank.data.TransactionDao
    private var cards: List<Card> = emptyList()
    private var selectedCard: Card? = null
    private var isFormatting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtherBankTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)
        val database = AppDatabase.getDatabase(this)
        cardDao = database.cardDao()
        transactionDao = database.transactionDao()

        setupClickListeners()
        setupCardNumberInput()
        loadCards()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.cvCard.setOnClickListener {
            showCardSelectionDialog()
        }

        binding.btnTransfer.setOnClickListener {
            performTransfer()
        }
    }

    private fun setupCardNumberInput() {
        binding.etCardNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                
                val text = s?.toString() ?: ""
                val digitsOnly = text.replace(" ", "").replace("-", "")
                
                // Ограничиваем до 16 цифр
                if (digitsOnly.length > 16) {
                    isFormatting = true
                    val limited = digitsOnly.take(16)
                    formatCardNumber(limited)
                    isFormatting = false
                    return
                }
                
                formatCardNumber(digitsOnly)
            }
        })
    }

    private fun formatCardNumber(digitsOnly: String) {
        if (isFormatting) return
        
        isFormatting = true
        try {
            if (digitsOnly.isEmpty()) {
                binding.etCardNumber.setText("")
                binding.etCardNumber.setSelection(0)
                return
            }
            
            val formatted = StringBuilder()
            for (i in digitsOnly.indices) {
                if (i > 0 && i % 4 == 0) {
                    formatted.append(" ")
                }
                formatted.append(digitsOnly[i])
            }
            
            val currentText = binding.etCardNumber.text.toString()
            if (formatted.toString() != currentText) {
                val cursorPos = binding.etCardNumber.selectionStart
                val oldText = currentText.replace(" ", "")
                val digitsBeforeCursor = oldText.substring(0, cursorPos.coerceAtMost(oldText.length)).length
                
                binding.etCardNumber.setText(formatted.toString())
                
                // Вычисляем новую позицию курсора
                var newCursorPos = 0
                var digitsCount = 0
                for (i in formatted.indices) {
                    if (formatted[i] != ' ') {
                        digitsCount++
                        if (digitsCount > digitsBeforeCursor) {
                            newCursorPos = i + 1
                            break
                        }
                    }
                }
                if (newCursorPos == 0) {
                    newCursorPos = formatted.length
                }
                
                binding.etCardNumber.setSelection(newCursorPos.coerceIn(0, formatted.length))
            }
        } finally {
            isFormatting = false
        }
    }

    private fun loadCards() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.getCards(userId)
                    .onSuccess { cardList ->
                        cards = cardList.filter { !it.isHidden && !it.isBlocked }
                        if (cards.isNotEmpty()) {
                            selectedCard = cards[0]
                            updateCardDisplay()
                        } else {
                            Toast.makeText(this@OtherBankTransferActivity, "У вас нет доступных карт", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(this@OtherBankTransferActivity, "Ошибка загрузки карт: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun showCardSelectionDialog() {
        if (cards.isEmpty()) {
            Toast.makeText(this, "У вас нет карт", Toast.LENGTH_SHORT).show()
            return
        }

        val cardTitles = cards.map { card ->
            val lastFour = card.number.takeLast(4)
            "•••• $lastFour (${formatBalance(card.balance)} ₸)"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите карту")
            .setItems(cardTitles.toTypedArray()) { _, which ->
                selectedCard = cards[which]
                updateCardDisplay()
            }
            .show()
    }

    private fun updateCardDisplay() {
        val button = binding.cvCard
        selectedCard?.let { card ->
            val lastFour = card.number.filter { it.isDigit() }.takeLast(4)
            val cardText = "•••• $lastFour"
            
            val format = NumberFormat.getNumberInstance(Locale.getDefault())
            format.maximumFractionDigits = 2
            format.minimumFractionDigits = 0
            val balanceFormatted = format.format(card.balance)
            
            // Обновляем текст кнопки
            button.text = "$cardText • $balanceFormatted ₸"
        } ?: run {
            button.text = "Выберите счет"
        }
    }

    private fun performTransfer() {
        val cardNumber = binding.etCardNumber.text.toString().replace(" ", "").replace("-", "")
        val amountText = binding.etAmount.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (selectedCard == null) {
            Toast.makeText(this, "Выберите карту", Toast.LENGTH_SHORT).show()
            return
        }

        if (cardNumber.length != 16 || !cardNumber.all { it.isDigit() }) {
            binding.tilCardNumber.error = "Номер карты должен содержать 16 цифр"
            return
        }

        if (amountText.isEmpty()) {
            binding.tilAmount.error = "Введите сумму"
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.tilAmount.error = "Введите корректную сумму"
            return
        }

        if (amount > (selectedCard?.balance ?: 0.0)) {
            binding.tilAmount.error = "Недостаточно средств"
            return
        }

        // Выполнение перевода в другой банк
        lifecycleScope.launch {
            try {
                val userId = securityPreferences.getCurrentUserId()
                if (userId == null) {
                    val intent = Intent(this@OtherBankTransferActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", "Ошибка авторизации")
                    startActivity(intent)
                    return@launch
                }

                val fromCardId = selectedCard?.id ?: return@launch
                
                // Загружаем актуальный баланс из БД перед переводом
                val currentFromCard = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val fromCardNumber = selectedCard?.number ?: ""
                    var card = cardDao.getCardByNumber(userId, fromCardNumber)
                    if (card == null) {
                        card = cardDao.getCardById(userId, fromCardId)
                    }
                    card
                }
                
                if (currentFromCard == null) {
                    val intent = Intent(this@OtherBankTransferActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", "Карта отправителя не найдена")
                    startActivity(intent)
                    return@launch
                }

                if (amount > currentFromCard.balance) {
                    binding.tilAmount.error = "Недостаточно средств"
                    return@launch
                }

                // Выполняем перевод в другой банк
                val result = userRepository.transferToOtherBank(fromCardId, cardNumber, amount, description.takeIf { it.isNotEmpty() })
                
                result.onSuccess { transferResponse ->
                    if (transferResponse.success) {
                        // Сохраняем транзакцию локально
                        val transaction = TransactionEntity(
                            userId = userId,
                            cardId = fromCardId,
                            title = "Перевод в другой банк",
                            amount = -amount,
                            type = TransactionType.TRANSFER_OUT.name,
                            timestamp = System.currentTimeMillis()
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            transactionDao.insertTransaction(transaction)
                        }

                        // Обновляем баланс карты отправителя локально
                        val newBalance = currentFromCard.balance - amount
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            cardDao.updateCardBalance(fromCardId, userId, newBalance)
                        }

                        // Переходим на экран успешного перевода
                        val intent = Intent(this@OtherBankTransferActivity, TransferSuccessActivity::class.java)
                        intent.putExtra("amount", amount)
                        // Получаем последние 4 цифры номера карты отправителя
                        // Номер карты может быть в формате "1234 5678 9012 3456" или зашифрован
                        val fromCardDigits = currentFromCard.number.filter { it.isDigit() }
                        val fromCardLastFour = if (fromCardDigits.length >= 4) {
                            fromCardDigits.takeLast(4)
                        } else {
                            // Fallback: берем последние 4 символа, если они цифры
                            currentFromCard.number.takeLast(4).filter { it.isDigit() }.takeLast(4)
                        }
                        intent.putExtra("from_card_number", "•••• $fromCardLastFour")
                        // Для перевода в другой банк показываем последние 4 цифры карты получателя
                        val toCardDigits = cardNumber.replace(" ", "").replace("-", "").filter { it.isDigit() }
                        val toCardLastFour = if (toCardDigits.length >= 4) {
                            toCardDigits.takeLast(4)
                        } else {
                            cardNumber.takeLast(4)
                        }
                        intent.putExtra("to_card_number", "•••• $toCardLastFour")
                        intent.putExtra("description", description.takeIf { it.isNotEmpty() })
                        transferResponse.transactionId?.let {
                            intent.putExtra("transaction_id", it.toString())
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        val errorMessage = transferResponse.message ?: "Ошибка при переводе"
                        val intent = Intent(this@OtherBankTransferActivity, TransferErrorActivity::class.java)
                        intent.putExtra("error_message", errorMessage)
                        startActivity(intent)
                    }
                }.onFailure { error ->
                    val intent = Intent(this@OtherBankTransferActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", error.message ?: "Ошибка при переводе")
                    startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("OtherBankTransfer", "Error performing transfer", e)
                val intent = Intent(this@OtherBankTransferActivity, TransferErrorActivity::class.java)
                intent.putExtra("error_message", "Неожиданная ошибка: ${e.message}")
                startActivity(intent)
            }
        }
    }

    private fun formatBalance(balance: Double): String {
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        return format.format(balance)
    }
}

