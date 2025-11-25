package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
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
import com.example.f_bank.databinding.ActivityTransferBetweenCardsBinding
import com.example.f_bank.databinding.BottomSheetCardSelectionBinding
import com.example.f_bank.databinding.ItemCardSelectionBinding
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class TransferBetweenCardsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransferBetweenCardsBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var cardDao: com.example.f_bank.data.CardDao
    private lateinit var transactionDao: com.example.f_bank.data.TransactionDao
    private var cards: List<Card> = emptyList()
    private var selectedFromCard: Card? = null
    private var selectedToCard: Card? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferBetweenCardsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)
        val database = AppDatabase.getDatabase(this)
        cardDao = database.cardDao()
        transactionDao = database.transactionDao()

        setupClickListeners()
        loadCards()
        // Инициализируем отображение кнопок выбора карт
        updateCardDisplays()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSelectFromCard.setOnClickListener {
            showCardSelectionBottomSheet(true)
        }

        binding.btnSelectToCard.setOnClickListener {
            showCardSelectionBottomSheet(false)
        }

        binding.btnTransfer.setOnClickListener {
            performTransfer()
        }
    }

    private fun loadCards() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.getCards(userId)
                    .onSuccess { cardList ->
                        cards = cardList.filter { !it.isHidden && !it.isBlocked }
                        // Не выбираем карты по умолчанию - пользователь должен выбрать сам
                        selectedFromCard = null
                        selectedToCard = null
                        updateCardDisplays()
                        
                        if (cards.isEmpty()) {
                            Toast.makeText(this@TransferBetweenCardsActivity, "У вас нет карт", Toast.LENGTH_SHORT).show()
                        } else if (cards.size < 2) {
                            Toast.makeText(this@TransferBetweenCardsActivity, "У вас должна быть минимум 2 карты для перевода", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(this@TransferBetweenCardsActivity, "Ошибка загрузки карт: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun showCardSelectionBottomSheet(isFromCard: Boolean) {
        if (cards.isEmpty()) {
            Toast.makeText(this, "У вас нет карт", Toast.LENGTH_SHORT).show()
            return
        }

        val availableCards = if (isFromCard) {
            cards
        } else {
            cards.filter { it.id != selectedFromCard?.id }
        }

        if (!isFromCard && availableCards.isEmpty()) {
            Toast.makeText(this, "Нет доступных карт для перевода", Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheetDialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetCardSelectionBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(sheetBinding.root)

        sheetBinding.tvSheetTitle.text = if (isFromCard) {
            "Выберите, откуда перевести"
        } else {
            "Выберите, куда перевести"
        }

        sheetBinding.btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        val adapter = CardSelectionAdapter(availableCards) { card ->
            if (isFromCard) {
                selectedFromCard = card
            } else {
                selectedToCard = card
            }
            updateCardDisplays()
            bottomSheetDialog.dismiss()
        }

        sheetBinding.rvCards.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvCards.adapter = adapter

        // Настраиваем поведение bottom sheet перед показом
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomSheetHeight = (screenHeight * 0.6).toInt()
        
        bottomSheetDialog.behavior.peekHeight = bottomSheetHeight
        bottomSheetDialog.behavior.isDraggable = true
        
        bottomSheetDialog.show()
    }

    private fun updateCardDisplays() {
        updateCardDisplay(binding.btnSelectFromCard, selectedFromCard)
        updateCardDisplay(binding.btnSelectToCard, selectedToCard)
    }

    private fun updateCardDisplay(button: com.google.android.material.button.MaterialButton, card: Card?) {
        if (card == null) {
            button.text = "Выберите счет"
            return
        }

        val lastFour = card.number.takeLast(4)
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        val balanceText = format.format(card.balance)
        val cardNumberText = "•••• $lastFour"
        val balanceFormatted = "$balanceText ₸"

        // Используем ViewTreeObserver для получения ширины кнопки после layout
        button.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                button.viewTreeObserver.removeOnGlobalLayoutListener(this)
                updateButtonText(button, cardNumberText, balanceFormatted)
            }
        })
        
        // Если кнопка уже имеет ширину, обновляем сразу
        if (button.width > 0) {
            updateButtonText(button, cardNumberText, balanceFormatted)
        }
    }

    private fun updateButtonText(button: com.google.android.material.button.MaterialButton, cardNumberText: String, balanceFormatted: String) {
        // Используем SpannableStringBuilder для выравнивания баланса справа
        val spannable = SpannableStringBuilder()
        spannable.append(cardNumberText)
        spannable.append(" ") // Один пробел между номером карты и балансом
        
        val balanceStart = spannable.length
        spannable.append(balanceFormatted)
        val balanceEnd = spannable.length
        
        // Применяем AlignmentSpan для выравнивания баланса справа
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
        val description = binding.etDescription.text.toString().trim()

        if (selectedFromCard == null) {
            Toast.makeText(this, "Выберите карту отправителя", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedToCard == null) {
            Toast.makeText(this, "Выберите карту получателя", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedFromCard?.id == selectedToCard?.id) {
            Toast.makeText(this, "Выберите разные карты", Toast.LENGTH_SHORT).show()
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

        // Выполнение перевода
        lifecycleScope.launch {
            try {
                val userId = securityPreferences.getCurrentUserId()
                if (userId == null) {
                    val intent = Intent(this@TransferBetweenCardsActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", "Ошибка авторизации")
                    startActivity(intent)
                    return@launch
                }

                val fromCardId = selectedFromCard?.id ?: return@launch
                val toCardId = selectedToCard?.id ?: return@launch
                
                android.util.Log.d("Transfer", "Starting transfer: userId=$userId, fromCardId=$fromCardId, toCardId=$toCardId")
                android.util.Log.d("Transfer", "Selected cards: fromCard=${selectedFromCard?.number?.takeLast(4)}, toCard=${selectedToCard?.number?.takeLast(4)}")
                
                // Синхронизируем карты с сервером перед переводом, чтобы получить правильные ID
                val token = securityPreferences.getAuthToken()
                if (token != null) {
                    android.util.Log.d("Transfer", "Syncing cards with server to get correct IDs...")
                    try {
                        userRepository.syncCardsFromServer(userId, token)
                        android.util.Log.d("Transfer", "Cards synced with server")
                    } catch (e: Exception) {
                        android.util.Log.w("Transfer", "Failed to sync cards with server, continuing with local IDs", e)
                    }
                }
                
                // Загружаем актуальные балансы из БД перед переводом
                // Используем номер карты для поиска, так как ID могут не совпадать
                val fromCardNumber = selectedFromCard?.number ?: ""
                val toCardNumber = selectedToCard?.number ?: ""
                
                val currentFromCard = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Сначала пробуем найти по номеру карты (более надежно)
                    var card = cardDao.getCardByNumber(userId, fromCardNumber)
                    if (card == null) {
                        // Если не найдено по номеру, пробуем по ID
                        android.util.Log.w("Transfer", "Card not found by number, trying by ID: $fromCardId")
                        card = cardDao.getCardById(userId, fromCardId)
                    }
                    if (card == null) {
                        android.util.Log.e("Transfer", "ERROR: fromCard not found in DB! cardId=$fromCardId, number=${fromCardNumber.takeLast(4)}, userId=$userId")
                    } else {
                        android.util.Log.d("Transfer", "fromCard found in DB: id=${card.id}, balance=${card.balance}, number=${card.number.takeLast(4)}")
                    }
                    card
                }
                val currentToCard = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Сначала пробуем найти по номеру карты (более надежно)
                    var card = cardDao.getCardByNumber(userId, toCardNumber)
                    if (card == null) {
                        // Если не найдено по номеру, пробуем по ID
                        android.util.Log.w("Transfer", "Card not found by number, trying by ID: $toCardId")
                        card = cardDao.getCardById(userId, toCardId)
                    }
                    if (card == null) {
                        android.util.Log.e("Transfer", "ERROR: toCard not found in DB! cardId=$toCardId, number=${toCardNumber.takeLast(4)}, userId=$userId")
                    } else {
                        android.util.Log.d("Transfer", "toCard found in DB: id=${card.id}, balance=${card.balance}, number=${card.number.takeLast(4)}")
                    }
                    card
                }
                
                if (currentFromCard == null || currentToCard == null) {
                    android.util.Log.e("Transfer", "ERROR: Cards not found in DB!")
                    val intent = Intent(this@TransferBetweenCardsActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", "Ошибка: карты не найдены в базе данных")
                    startActivity(intent)
                    return@launch
                }
                
                // Сохраняем старые балансы для возможного отката
                val oldFromBalance = currentFromCard.balance
                val oldToBalance = currentToCard.balance
                
                android.util.Log.d("Transfer", "Current balances from DB: fromCard=$oldFromBalance, toCard=$oldToBalance")
                
                // Вычисляем новые балансы
                val newFromBalance = oldFromBalance - amount
                val newToBalance = oldToBalance + amount
                
                // Объявляем переменную для transactionId до блока withContext
                var transactionId: Long? = null
                
                // Обновляем балансы в базе данных
                // ВАЖНО: Обновляем балансы ДО симуляции API, чтобы они точно сохранились
                try {
                    android.util.Log.d("Transfer", "Starting balance and transaction update in DB")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            // Используем актуальные ID из БД после синхронизации
                            // После синхронизации карты должны иметь правильные ID с сервера
                            val actualFromCardId = currentFromCard.id
                            val actualToCardId = currentToCard.id
                            
                            android.util.Log.d("Transfer", "Using card IDs for backend: fromCardId=$actualFromCardId, toCardId=$actualToCardId")
                            android.util.Log.d("Transfer", "Card numbers: fromCard=${currentFromCard.number.takeLast(4)}, toCard=${currentToCard.number.takeLast(4)}")
                            
                            android.util.Log.d("Transfer", "Updating balance: cardId=$actualFromCardId (original=$fromCardId), userId=$userId, oldBalance=$oldFromBalance, newBalance=$newFromBalance")
                            val rowsUpdatedFrom = cardDao.updateCardBalance(actualFromCardId, userId, newFromBalance)
                            android.util.Log.d("Transfer", "Rows updated for fromCard: $rowsUpdatedFrom")
                            
                            if (rowsUpdatedFrom == 0) {
                                android.util.Log.e("Transfer", "ERROR: No rows updated for fromCard! cardId=$actualFromCardId, userId=$userId")
                                // Пробуем обновить по номеру карты
                                android.util.Log.d("Transfer", "Trying to update by card number...")
                                cardDao.updateCardByNumber(
                                    userId = userId,
                                    cardNumber = currentFromCard.number,
                                    balance = newFromBalance,
                                    type = currentFromCard.type,
                                    currency = currentFromCard.currency,
                                    expiry = currentFromCard.expiry
                                )
                            }
                            
                            android.util.Log.d("Transfer", "Updating balance: cardId=$actualToCardId (original=$toCardId), userId=$userId, oldBalance=$oldToBalance, newBalance=$newToBalance")
                            val rowsUpdatedTo = cardDao.updateCardBalance(actualToCardId, userId, newToBalance)
                            android.util.Log.d("Transfer", "Rows updated for toCard: $rowsUpdatedTo")
                            
                            if (rowsUpdatedTo == 0) {
                                android.util.Log.e("Transfer", "ERROR: No rows updated for toCard! cardId=$actualToCardId, userId=$userId")
                                // Пробуем обновить по номеру карты
                                android.util.Log.d("Transfer", "Trying to update by card number...")
                                cardDao.updateCardByNumber(
                                    userId = userId,
                                    cardNumber = currentToCard.number,
                                    balance = newToBalance,
                                    type = currentToCard.type,
                                    currency = currentToCard.currency,
                                    expiry = currentToCard.expiry
                                )
                            }
                            
                            // Проверяем, что балансы обновились
                            val updatedFromCard = cardDao.getCardById(userId, actualFromCardId)
                            val updatedToCard = cardDao.getCardById(userId, actualToCardId)
                            
                            if (updatedFromCard == null) {
                                android.util.Log.e("Transfer", "ERROR: fromCard not found after update! cardId=$actualFromCardId, userId=$userId")
                            } else {
                                android.util.Log.d("Transfer", "Updated fromCard balance: ${updatedFromCard.balance}, expected: $newFromBalance")
                                if (kotlin.math.abs(updatedFromCard.balance - newFromBalance) > 0.01) {
                                    android.util.Log.e("Transfer", "ERROR: Balance mismatch! Expected: $newFromBalance, Got: ${updatedFromCard.balance}")
                                } else {
                                    android.util.Log.d("Transfer", "SUCCESS: fromCard balance updated correctly!")
                                }
                            }
                            
                            if (updatedToCard == null) {
                                android.util.Log.e("Transfer", "ERROR: toCard not found after update! cardId=$actualToCardId, userId=$userId")
                            } else {
                                android.util.Log.d("Transfer", "Updated toCard balance: ${updatedToCard.balance}, expected: $newToBalance")
                                if (kotlin.math.abs(updatedToCard.balance - newToBalance) > 0.01) {
                                    android.util.Log.e("Transfer", "ERROR: Balance mismatch! Expected: $newToBalance, Got: ${updatedToCard.balance}")
                                } else {
                                    android.util.Log.d("Transfer", "SUCCESS: toCard balance updated correctly!")
                                }
                            }
                            
                            // Обновляем локальные переменные карт с новыми балансами из БД
                            selectedFromCard = updatedFromCard ?: selectedFromCard?.copy(balance = newFromBalance)
                            selectedToCard = updatedToCard ?: selectedToCard?.copy(balance = newToBalance)
                            
                            // Сохраняем транзакцию для карты отправителя (исходящий перевод)
                            val toCardLast4 = selectedToCard?.number?.takeLast(4) ?: "****"
                            val fromCardNumber = currentFromCard.number
                            val toCardNumber = currentToCard.number
                            val transferOutTransaction = TransactionEntity(
                                userId = userId,
                                cardId = actualFromCardId,
                                title = "Перевод на карту •••• $toCardLast4",
                                amount = -amount,
                                type = TransactionType.TRANSFER_OUT.name,
                                timestamp = System.currentTimeMillis(),
                                iconRes = R.drawable.ic_transfer_up,
                                toCardId = actualToCardId,
                                fromCardId = actualFromCardId
                            )
                            // Сохраняем номера карт в дополнительных полях (если нужно)
                            android.util.Log.d("Transfer", "Saving transaction: fromCard=${fromCardNumber.takeLast(4)}, toCard=${toCardNumber.takeLast(4)}")
                            android.util.Log.d("Transfer", "Saving transferOutTransaction: userId=$userId, cardId=$actualFromCardId, amount=$amount")
                            try {
                                val transactionId1 = transactionDao.insertTransaction(transferOutTransaction)
                                android.util.Log.d("Transfer", "TransferOutTransaction saved with id: $transactionId1")
                            } catch (e: Exception) {
                                android.util.Log.e("Transfer", "Error saving transferOutTransaction", e)
                                throw e
                            }
                            
                            // Сохраняем транзакцию для карты получателя (входящий перевод)
                            val fromCardLast4 = selectedFromCard?.number?.takeLast(4) ?: "****"
                            val transferInTransaction = TransactionEntity(
                                userId = userId,
                                cardId = actualToCardId,
                                title = "Перевод с карты •••• $fromCardLast4",
                                amount = amount,
                                type = TransactionType.TRANSFER_IN.name,
                                timestamp = System.currentTimeMillis(),
                                iconRes = R.drawable.ic_transfer_up,
                                toCardId = actualToCardId,
                                fromCardId = actualFromCardId
                            )
                            android.util.Log.d("Transfer", "Saving transferInTransaction: userId=$userId, cardId=$actualToCardId, amount=$amount")
                            try {
                                val transactionId2 = transactionDao.insertTransaction(transferInTransaction)
                                android.util.Log.d("Transfer", "TransferInTransaction saved with id: $transactionId2")
                            } catch (e: Exception) {
                                android.util.Log.e("Transfer", "Error saving transferInTransaction", e)
                                throw e
                            }
                            
                            // Проверяем, что транзакции действительно сохранились
                            val savedTransactions = transactionDao.getTransactionsByUserId(userId, limit = 10)
                            android.util.Log.d("Transfer", "=== TRANSACTION SAVE VERIFICATION ===")
                            android.util.Log.d("Transfer", "Total transactions in DB after save: ${savedTransactions.size}")
                            android.util.Log.d("Transfer", "Looking for transactions with userId=$userId")
                            savedTransactions.forEach { tx ->
                                android.util.Log.d("Transfer", "Found transaction: id=${tx.id}, userId=${tx.userId}, cardId=${tx.cardId}, title=${tx.title}, amount=${tx.amount}, type=${tx.type}, timestamp=${tx.timestamp}")
                            }
                            
                            // Проверяем, что наши транзакции там есть
                            val ourTransactions = savedTransactions.filter { 
                                (it.cardId == actualFromCardId || it.cardId == actualToCardId) && 
                                (it.type == TransactionType.TRANSFER_OUT.name || it.type == TransactionType.TRANSFER_IN.name)
                            }
                            android.util.Log.d("Transfer", "Our transactions found: ${ourTransactions.size} (expected: 2)")
                            if (ourTransactions.size < 2) {
                                android.util.Log.e("Transfer", "ERROR: Not all transactions were saved! Expected 2, found ${ourTransactions.size}")
                            } else {
                                android.util.Log.d("Transfer", "SUCCESS: All transactions saved correctly!")
                            }
                            android.util.Log.d("Transfer", "=== END VERIFICATION ===")
                            
                            // Сохраняем время последнего перевода для предотвращения перезаписи балансов с сервера
                            securityPreferences.saveLastTransferTime()
                            
                            android.util.Log.d("Transfer", "All data saved successfully!")
                            
                            // Отправляем перевод на сервер через API
                            val token = securityPreferences.getAuthToken()
                            val apiSuccess = if (token != null) {
                                try {
                                    android.util.Log.d("Transfer", "=== SENDING TRANSFER TO BACKEND ===")
                                    android.util.Log.d("Transfer", "Token: ${token.take(20)}...")
                                    android.util.Log.d("Transfer", "fromCardId=$actualFromCardId, toCardId=$actualToCardId, amount=$amount")
                                    
                                    val transferRequest = com.example.f_bank.api.model.TransferRequest(
                                        fromCardId = actualFromCardId,
                                        toCardId = actualToCardId,
                                        amount = amount,
                                        description = description.takeIf { it.isNotEmpty() } ?: "Перевод между картами"
                                    )
                                    
                                    val apiService = com.example.f_bank.api.RetrofitClient.apiService
                                    android.util.Log.d("Transfer", "Calling API: POST /api/cards/transfer")
                                    val response = apiService.transferBetweenCards("Bearer $token", transferRequest)
                                    
                                    android.util.Log.d("Transfer", "API Response: code=${response.code()}, isSuccessful=${response.isSuccessful}")
                                    
                                    if (response.isSuccessful && response.body() != null) {
                                        val transferResponse = response.body()!!
                                        android.util.Log.d("Transfer", "Backend transfer response: success=${transferResponse.success}, message=${transferResponse.message}, transactionId=${transferResponse.transactionId}")
                                        
                                        // Сохраняем transactionId для квитанции
                                        transactionId = transferResponse.transactionId
                                        
                                        if (transferResponse.success) {
                                            android.util.Log.d("Transfer", "SUCCESS: Transfer saved on backend! Transaction ID: ${transferResponse.transactionId}")
                                            
                                            // Синхронизируем карты с сервером, чтобы обновить балансы
                                            android.util.Log.d("Transfer", "Syncing cards with server to update balances...")
                                            try {
                                                userRepository.syncCardsFromServer(userId, token)
                                                android.util.Log.d("Transfer", "Cards synced successfully, balances updated")
                                                
                                                // Обновляем локальные переменные карт после синхронизации
                                                val syncedFromCard = cardDao.getCardByNumber(userId, currentFromCard.number)
                                                val syncedToCard = cardDao.getCardByNumber(userId, currentToCard.number)
                                                
                                                if (syncedFromCard != null) {
                                                    selectedFromCard = syncedFromCard
                                                    android.util.Log.d("Transfer", "From card balance synced: ${syncedFromCard.balance}")
                                                }
                                                if (syncedToCard != null) {
                                                    selectedToCard = syncedToCard
                                                    android.util.Log.d("Transfer", "To card balance synced: ${syncedToCard.balance}")
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("Transfer", "Error syncing cards after transfer", e)
                                            }
                                        } else {
                                            android.util.Log.e("Transfer", "Backend returned success=false: ${transferResponse.message}")
                                        }
                                        
                                        transferResponse.success
                                    } else {
                                        val errorBody = response.errorBody()?.string()
                                        android.util.Log.e("Transfer", "Backend transfer failed: code=${response.code()}, message=${response.message()}")
                                        android.util.Log.e("Transfer", "Error body: $errorBody")
                                        // Продолжаем выполнение даже если API вернул ошибку (офлайн режим)
                                        true
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("Transfer", "Exception calling backend API", e)
                                    e.printStackTrace()
                                    // Продолжаем выполнение даже если API недоступен (офлайн режим)
                                    true
                                }
                            } else {
                                android.util.Log.w("Transfer", "No auth token found, skipping backend API call")
                                // Продолжаем выполнение без токена (офлайн режим)
                                true
                            }
                            android.util.Log.d("Transfer", "=== BACKEND API CALL COMPLETED ===")
                            
                            apiSuccess
                        }
                        
                        // Обновляем отображение карт на UI
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            updateCardDisplays()
                        }
                        
                        // Переход на экран успешного перевода (данные уже сохранены локально)
                        // API вызов выполнен внутри блока выше
                        val success = true // Локальные данные сохранены успешно
                        
                        if (success) {
                            // Переход на экран успешного перевода
                            val intent = Intent(this@TransferBetweenCardsActivity, TransferSuccessActivity::class.java)
                            intent.putExtra("from_card_number", "•••• ${selectedFromCard?.number?.takeLast(4)}")
                            intent.putExtra("to_card_number", "•••• ${selectedToCard?.number?.takeLast(4)}")
                            intent.putExtra("amount", amount)
                            // Передаем описание и ID транзакции, если есть
                            val description = binding.etDescription.text.toString().trim()
                            intent.putExtra("description", description.takeIf { it.isNotEmpty() })
                            transactionId?.let {
                                intent.putExtra("transaction_id", it.toString())
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            // Переход на экран ошибки (балансы уже обновлены, но можно откатить)
                            val intent = Intent(this@TransferBetweenCardsActivity, TransferErrorActivity::class.java)
                            intent.putExtra("error_message", "Не удалось выполнить перевод. Попробуйте позже.")
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TransferBetweenCards", "CRITICAL ERROR saving transaction or balance", e)
                        e.printStackTrace()
                        // Показываем полный стек трейс для отладки
                        android.util.Log.e("TransferBetweenCards", "Exception stack trace:", e)
                        // Переход на экран ошибки
                        val intent = Intent(this@TransferBetweenCardsActivity, TransferErrorActivity::class.java)
                        intent.putExtra("error_message", "Ошибка при сохранении перевода: ${e.message}")
                        startActivity(intent)
                    }
            } catch (e: Exception) {
                // Переход на экран ошибки при исключении
                val intent = Intent(this@TransferBetweenCardsActivity, TransferErrorActivity::class.java)
                intent.putExtra("error_message", "Ошибка: ${e.message}")
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


