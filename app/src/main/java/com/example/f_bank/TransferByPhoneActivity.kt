package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.AlignmentSpan
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.Card
import com.example.f_bank.data.TransactionEntity
import com.example.f_bank.data.TransactionType
import com.example.f_bank.data.UserRepository
import com.example.f_bank.R
import com.example.f_bank.databinding.ActivityTransferByPhoneBinding
import com.example.f_bank.databinding.BottomSheetCardSelectionBinding
import com.example.f_bank.databinding.ItemCardSelectionBinding
import com.example.f_bank.utils.PhoneNumberFormatter
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class TransferByPhoneActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransferByPhoneBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var cardDao: com.example.f_bank.data.CardDao
    private lateinit var transactionDao: com.example.f_bank.data.TransactionDao
    private var cards: List<Card> = emptyList()
    private var selectedFromCard: Card? = null
    private var fetchUserNameJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferByPhoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)
        val database = AppDatabase.getDatabase(this)
        cardDao = database.cardDao()
        transactionDao = database.transactionDao()

        setupClickListeners()
        setupPhoneFormatter()
        loadCards()
        updateCardDisplay()
        
        // Получаем номер телефона из Intent, если он передан
        val phoneFromIntent = intent.getStringExtra("phone")
        if (!phoneFromIntent.isNullOrEmpty()) {
            // Форматируем номер телефона
            val cleanedPhone = PhoneNumberFormatter.unformatPhone(phoneFromIntent)
            val formattedPhone = PhoneNumberFormatter.formatDigits(cleanedPhone)
            binding.etPhoneNumber.setText(formattedPhone)
            // Вызываем fetchUserName напрямую для получения имени
            if (cleanedPhone.length == 11 && cleanedPhone.startsWith("7")) {
                fetchUserName(cleanedPhone)
            }
        }
        
        setupPhoneNumberWatcher()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSelectFromCard.setOnClickListener {
            showCardSelectionBottomSheet()
        }

        binding.btnTransfer.setOnClickListener {
            performTransfer()
        }
    }
    
    private fun setupPhoneFormatter() {
        val phoneFormatter = PhoneNumberFormatter(binding.etPhoneNumber)
        binding.etPhoneNumber.addTextChangedListener(phoneFormatter)
    }
    
    private fun setupPhoneNumberWatcher() {
        binding.etPhoneNumber.addTextChangedListener(object : TextWatcher {
            private var lastPhoneNumber = ""
            private var isProcessing = false
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (isProcessing) return
                
                val phoneText = s?.toString() ?: ""
                val cleanedPhone = PhoneNumberFormatter.unformatPhone(phoneText)
                
                // Проверяем, что номер изменился и имеет правильный формат
                if (cleanedPhone != lastPhoneNumber) {
                    lastPhoneNumber = cleanedPhone
                    
                    // Если номер пустой или неполный, скрываем имя
                    if (cleanedPhone.isEmpty() || cleanedPhone.length < 11) {
                        binding.tvRecipientName.visibility = android.view.View.GONE
                        binding.tvRecipientName.text = ""
                        return
                    }
                    
                    // Проверяем формат номера (должен быть 11 цифр, начинаваться с 7)
                    if (cleanedPhone.length == 11 && cleanedPhone.startsWith("7")) {
                        // Запрашиваем имя пользователя
                        fetchUserName(cleanedPhone)
                    } else {
                        binding.tvRecipientName.visibility = android.view.View.GONE
                        binding.tvRecipientName.text = ""
                    }
                }
            }
        })
    }
    
    private fun fetchUserName(phone: String) {
        // Отменяем предыдущий запрос, если он еще выполняется
        fetchUserNameJob?.cancel()
        
        fetchUserNameJob = lifecycleScope.launch {
            try {
                // Добавляем небольшую задержку (debounce) перед запросом
                delay(500) // 500ms задержка после последнего изменения
                
                userRepository.getUserByPhone(phone)
                    .onSuccess { userName ->
                        if (userName != null) {
                            binding.tvRecipientName.text = userName
                            binding.tvRecipientName.visibility = android.view.View.VISIBLE
                        } else {
                            binding.tvRecipientName.visibility = android.view.View.GONE
                            binding.tvRecipientName.text = ""
                        }
                    }
                    .onFailure { error ->
                        // При ошибке просто скрываем имя (не показываем ошибку пользователю)
                        binding.tvRecipientName.visibility = android.view.View.GONE
                        binding.tvRecipientName.text = ""
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Игнорируем отмену корутины
            } catch (e: Exception) {
                // Игнорируем ошибки при получении имени
                binding.tvRecipientName.visibility = android.view.View.GONE
                binding.tvRecipientName.text = ""
            }
        }
    }

    private fun loadCards() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.getCards(userId)
                    .onSuccess { cardList ->
                        cards = cardList.filter { !it.isHidden && !it.isBlocked }
                        selectedFromCard = null
                        updateCardDisplay()
                        
                        if (cards.isEmpty()) {
                            Toast.makeText(this@TransferByPhoneActivity, "У вас нет карт", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(this@TransferByPhoneActivity, "Ошибка загрузки карт: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun showCardSelectionBottomSheet() {
        if (cards.isEmpty()) {
            Toast.makeText(this, "У вас нет доступных карт", Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheet = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetCardSelectionBinding.inflate(layoutInflater)
        bottomSheet.setContentView(bottomSheetBinding.root)

        bottomSheetBinding.rvCards.layoutManager = LinearLayoutManager(this)
        bottomSheetBinding.rvCards.adapter = CardSelectionAdapter(cards) { card ->
            selectedFromCard = card
            updateCardDisplay()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun updateCardDisplay() {
        val button = binding.btnSelectFromCard
        if (selectedFromCard == null) {
            button.text = "Выберите счет"
            return
        }

        val card = selectedFromCard!!
        val lastFour = card.number.takeLast(4)
        val cardText = "•••• $lastFour"

        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        val balanceFormatted = "${format.format(card.balance)} ₸"

        val spannable = SpannableStringBuilder()
        spannable.append(cardText)
        spannable.append("\n")
        val balanceStart = spannable.length
        spannable.append(balanceFormatted)
        val balanceEnd = spannable.length

        spannable.setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
            balanceStart,
            balanceEnd,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        button.text = spannable
    }

    private class CardSelectionAdapter(
        private val cards: List<Card>,
        private val onCardClick: (Card) -> Unit
    ) : RecyclerView.Adapter<CardSelectionAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemCardSelectionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCardSelectionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val card = cards[position]
            val lastFour = card.number.takeLast(4)
            holder.binding.tvCardNumber.text = "•••• $lastFour"
            
            val format = NumberFormat.getNumberInstance(Locale.getDefault())
            format.maximumFractionDigits = 2
            format.minimumFractionDigits = 0
            holder.binding.tvCardBalance.text = "Баланс: ${format.format(card.balance)} ₸"

            holder.itemView.setOnClickListener {
                onCardClick(card)
            }
        }

        override fun getItemCount() = cards.size
    }

    private fun performTransfer() {
        val amountText = binding.etAmount.text.toString().trim()
        val phoneText = binding.etPhoneNumber.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (selectedFromCard == null) {
            Toast.makeText(this, "Выберите карту отправителя", Toast.LENGTH_SHORT).show()
            return
        }

        if (phoneText.isEmpty()) {
            binding.tilPhoneNumber.error = "Введите номер телефона"
            return
        }

        val cleanedPhone = PhoneNumberFormatter.unformatPhone(phoneText)
        if (cleanedPhone.length != 11 || !cleanedPhone.startsWith("7")) {
            binding.tilPhoneNumber.error = "Введите корректный номер телефона (7XXXXXXXXXX)"
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

        if (amount > (selectedFromCard?.balance ?: 0.0)) {
            binding.tilAmount.error = "Недостаточно средств"
            return
        }

        // Выполнение перевода по номеру телефона
        lifecycleScope.launch {
            try {
                val userId = securityPreferences.getCurrentUserId()
                if (userId == null) {
                    val intent = Intent(this@TransferByPhoneActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", "Ошибка авторизации")
                    startActivity(intent)
                    return@launch
                }

                val fromCardId = selectedFromCard?.id ?: return@launch
                
                // Загружаем актуальный баланс из БД перед переводом
                val currentFromCard = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val fromCardNumber = selectedFromCard?.number ?: ""
                    var card = cardDao.getCardByNumber(userId, fromCardNumber)
                    if (card == null) {
                        card = cardDao.getCardById(userId, fromCardId)
                    }
                    card
                }
                
                if (currentFromCard == null) {
                    val intent = Intent(this@TransferByPhoneActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", "Карта отправителя не найдена")
                    startActivity(intent)
                    return@launch
                }

                if (amount > currentFromCard.balance) {
                    binding.tilAmount.error = "Недостаточно средств"
                    return@launch
                }

                // Выполняем перевод по номеру телефона
                val result = userRepository.transferByPhone(fromCardId, cleanedPhone, amount, description.takeIf { it.isNotEmpty() })
                
                result.onSuccess { transferResponse ->
                    if (transferResponse.success) {
                        // Сохраняем транзакцию локально
                        val transaction = TransactionEntity(
                            userId = userId,
                            cardId = fromCardId,
                            title = "Перевод по номеру телефона",
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
                        val intent = Intent(this@TransferByPhoneActivity, TransferSuccessActivity::class.java)
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
                        // Для перевода по телефону показываем номер телефона
                        intent.putExtra("to_card_number", PhoneNumberFormatter.formatDigits(cleanedPhone))
                        intent.putExtra("description", description.takeIf { it.isNotEmpty() })
                        transferResponse.transactionId?.let {
                            intent.putExtra("transaction_id", it.toString())
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        val errorMessage = transferResponse.message ?: "Ошибка при переводе"
                        val intent = Intent(this@TransferByPhoneActivity, TransferErrorActivity::class.java)
                        intent.putExtra("error_message", errorMessage)
                        startActivity(intent)
                    }
                }.onFailure { error ->
                    val intent = Intent(this@TransferByPhoneActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", error.message ?: "Ошибка при переводе")
                    startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("TransferByPhone", "Error performing transfer", e)
                val intent = Intent(this@TransferByPhoneActivity, TransferErrorActivity::class.java)
                intent.putExtra("error_message", "Неожиданная ошибка: ${e.message}")
                startActivity(intent)
            }
        }
    }
}

