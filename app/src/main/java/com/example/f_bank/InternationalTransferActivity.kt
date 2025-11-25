package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.Card
import com.example.f_bank.data.TransactionEntity
import com.example.f_bank.data.TransactionType
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityInternationalTransferBinding
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.PhoneNumberFormatter
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class InternationalTransferActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInternationalTransferBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var cardDao: com.example.f_bank.data.CardDao
    private lateinit var transactionDao: com.example.f_bank.data.TransactionDao
    private var cards: List<Card> = emptyList()
    private var selectedCard: Card? = null
    
    private val currencies = listOf("USD", "EUR", "GBP", "RUB", "CNY", "JPY", "KZT")
    private val transferSystems = listOf("SWIFT", "Western Union", "Korona Pay")
    private var selectedTransferSystem: String = transferSystems[0]
    
    private val countries = listOf(
        "Россия", "США", "Великобритания", "Германия", "Франция", "Италия", "Испания",
        "Польша", "Нидерланды", "Бельгия", "Швейцария", "Австрия", "Швеция", "Норвегия",
        "Дания", "Финляндия", "Португалия", "Греция", "Чехия", "Венгрия", "Румыния",
        "Болгария", "Хорватия", "Словакия", "Словения", "Литва", "Латвия", "Эстония",
        "Китай", "Япония", "Южная Корея", "Индия", "Турция", "ОАЭ", "Саудовская Аравия",
         "Узбекистан", "Кыргызстан", "Таджикистан", "Туркменистан", "Азербайджан",
        "Армения", "Грузия", "Украина", "Беларусь", "Молдова", "Канада", "Мексика",
        "Бразилия", "Аргентина", "Чили", "Австралия", "Новая Зеландия", "ЮАР", "Египет",
        "Израиль", "Таиланд", "Сингапур", "Малайзия", "Индонезия", "Филиппины", "Вьетнам"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInternationalTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)
        val database = AppDatabase.getDatabase(this)
        cardDao = database.cardDao()
        transactionDao = database.transactionDao()

        setupClickListeners()
        setupCurrencySpinner()
        setupTransferSystemSelector()
        setupCountrySelector()
        setupPhoneNumberInput()
        loadCards()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSelectFromCard.setOnClickListener {
            showCardSelectionDialog()
        }

        binding.btnTransfer.setOnClickListener {
            performTransfer()
        }
    }
    
    private fun setupCurrencySpinner() {
        binding.etCurrency.setText(currencies[0]) // USD по умолчанию
        binding.etCurrency.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Выберите валюту")
                .setItems(currencies.toTypedArray()) { _, which ->
                    binding.etCurrency.setText(currencies[which])
                }
                .show()
        }
    }
    
    private fun setupTransferSystemSelector() {
        binding.etTransferSystem.setText(transferSystems[0]) // SWIFT по умолчанию
        binding.etTransferSystem.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Выберите систему перевода")
                .setItems(transferSystems.toTypedArray()) { _, which ->
                    selectedTransferSystem = transferSystems[which]
                    binding.etTransferSystem.setText(selectedTransferSystem)
                    updateFieldsVisibility()
                }
                .show()
        }
        updateFieldsVisibility()
    }
    
    private fun setupCountrySelector() {
        // Устанавливаем значение по умолчанию
        binding.etCountry.setText("Россия")
        
        binding.etCountry.setOnClickListener {
            showCountrySelectionDialog()
        }
    }
    
    private fun showCountrySelectionDialog() {
        // Создаем адаптер для списка стран
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, countries)
        
        // Создаем SearchView для поиска
        val searchView = android.widget.SearchView(this).apply {
            queryHint = "Поиск страны..."
            setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false
                
                override fun onQueryTextChange(newText: String?): Boolean {
                    adapter.filter.filter(newText)
                    return true
                }
            })
        }
        
        // Создаем ListView для отображения стран
        val listView = android.widget.ListView(this).apply {
            this.adapter = adapter
        }
        
        // Создаем контейнер для SearchView и ListView
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val margin = (32 * resources.displayMetrics.density).toInt()
            setPadding(margin, margin / 2, margin, margin / 2)
            
            addView(searchView, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            
            val listViewParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.5).toInt()
            )
            listViewParams.topMargin = (16 * resources.displayMetrics.density).toInt()
            addView(listView, listViewParams)
        }
        
        // Создаем и показываем диалог
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите страну")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .create()
        
        // Устанавливаем обработчик клика на элемент списка
        listView.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedCountry = adapter.getItem(position) ?: ""
            binding.etCountry.setText(selectedCountry)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun setupPhoneNumberInput() {
        PhoneNumberFormatter(binding.etReceiverPhone)
    }
    
    private fun updateFieldsVisibility() {
        when (selectedTransferSystem) {
            "SWIFT" -> {
                // Показываем SWIFT и IBAN поля
                binding.tvSwiftLabel.visibility = android.view.View.VISIBLE
                binding.tilSwiftCode.visibility = android.view.View.VISIBLE
                binding.tvIbanLabel.visibility = android.view.View.VISIBLE
                binding.tilIban.visibility = android.view.View.VISIBLE
                binding.tvCountryLabel.visibility = android.view.View.VISIBLE
                binding.tilCountry.visibility = android.view.View.VISIBLE
                // Скрываем телефон получателя
                binding.tvReceiverPhoneLabel.visibility = android.view.View.GONE
                binding.tilReceiverPhone.visibility = android.view.View.GONE
            }
            "Western Union", "Korona Pay" -> {
                // Скрываем SWIFT и IBAN поля
                binding.tvSwiftLabel.visibility = android.view.View.GONE
                binding.tilSwiftCode.visibility = android.view.View.GONE
                binding.tvIbanLabel.visibility = android.view.View.GONE
                binding.tilIban.visibility = android.view.View.GONE
                // Показываем телефон получателя
                binding.tvReceiverPhoneLabel.visibility = android.view.View.VISIBLE
                binding.tilReceiverPhone.visibility = android.view.View.VISIBLE
                // Страна остается видимой
                binding.tvCountryLabel.visibility = android.view.View.VISIBLE
                binding.tilCountry.visibility = android.view.View.VISIBLE
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
                        if (cards.isNotEmpty()) {
                            selectedCard = cards[0]
                            updateCardDisplay()
                        } else {
                            Toast.makeText(this@InternationalTransferActivity, "У вас нет доступных карт", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(this@InternationalTransferActivity, "Ошибка загрузки карт: ${error.message}", Toast.LENGTH_SHORT).show()
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
            val lastFour = card.number.filter { it.isDigit() }.takeLast(4)
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
        selectedCard?.let { card ->
            val lastFour = card.number.filter { it.isDigit() }.takeLast(4)
            val cardText = "•••• $lastFour"
            
            val format = NumberFormat.getNumberInstance(Locale.getDefault())
            format.maximumFractionDigits = 2
            format.minimumFractionDigits = 0
            val balanceFormatted = format.format(card.balance)
            
            binding.btnSelectFromCard.text = "$cardText • $balanceFormatted ₸"
        } ?: run {
            binding.btnSelectFromCard.text = "Выберите счет"
        }
    }

    private fun formatBalance(balance: Double): String {
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        return format.format(balance)
    }

    private fun performTransfer() {
        val recipientName = binding.etRecipientName.text.toString().trim()
        val swiftCode = binding.etSwiftCode.text.toString().trim().uppercase()
        val iban = binding.etIban.text.toString().trim().uppercase()
        val receiverPhone = binding.etReceiverPhone.text.toString().trim()
        val country = binding.etCountry.text.toString().trim()
        val amountText = binding.etAmount.text.toString().trim()
        val currency = binding.etCurrency.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (selectedCard == null) {
            Toast.makeText(this, "Выберите карту", Toast.LENGTH_SHORT).show()
            return
        }

        if (recipientName.isEmpty()) {
            binding.tilRecipientName.error = "Введите имя получателя"
            return
        } else {
            binding.tilRecipientName.error = null
        }

        if (swiftCode.isEmpty()) {
            binding.tilSwiftCode.error = "Введите SWIFT код"
            return
        } else if (swiftCode.length < 8 || swiftCode.length > 11) {
            binding.tilSwiftCode.error = "SWIFT код должен содержать 8-11 символов"
            return
        } else {
            binding.tilSwiftCode.error = null
        }

        if (iban.isEmpty()) {
            binding.tilIban.error = "Введите IBAN"
            return
        } else if (iban.length < 15 || iban.length > 34) {
            binding.tilIban.error = "IBAN должен содержать 15-34 символа"
            return
        } else {
            binding.tilIban.error = null
        }

        if (country.isEmpty()) {
            binding.tilCountry.error = "Введите страну получателя"
            return
        } else {
            binding.tilCountry.error = null
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

        if (currency.isEmpty()) {
            Toast.makeText(this, "Выберите валюту", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val userId = securityPreferences.getCurrentUserId()
                if (userId == null) {
                    val intent = Intent(this@InternationalTransferActivity, TransferErrorActivity::class.java)
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
                    val intent = Intent(this@InternationalTransferActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", "Карта отправителя не найдена")
                    startActivity(intent)
                    return@launch
                }

                if (amount > currentFromCard.balance) {
                    binding.tilAmount.error = "Недостаточно средств"
                    return@launch
                }

                // Нормализуем телефон для Western Union/Korona Pay
                val normalizedPhone = if (selectedTransferSystem in listOf("Western Union", "Korona Pay")) {
                    PhoneNumberFormatter.unformatPhone(receiverPhone)
                } else {
                    receiverPhone
                }
                
                // Выполняем международный перевод
                val result = userRepository.internationalTransfer(
                    fromCardId, 
                    selectedTransferSystem,
                    recipientName, 
                    swiftCode.takeIf { it.isNotEmpty() }, 
                    iban.takeIf { it.isNotEmpty() }, 
                    normalizedPhone.takeIf { it.isNotEmpty() },
                    country, 
                    amount, 
                    currency,
                    description.takeIf { it.isNotEmpty() }
                )

                result.onSuccess { transferResponse ->
                    if (transferResponse.success) {
                        // Сохраняем транзакцию локально
                        val transaction = TransactionEntity(
                            userId = userId,
                            cardId = fromCardId,
                            title = "Международный перевод ($currency)",
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
                        val intent = Intent(this@InternationalTransferActivity, TransferSuccessActivity::class.java)
                        intent.putExtra("amount", amount)
                        // Получаем последние 4 цифры номера карты отправителя
                        val fromCardDigits = currentFromCard.number.filter { it.isDigit() }
                        val fromCardLastFour = if (fromCardDigits.length >= 4) {
                            fromCardDigits.takeLast(4)
                        } else {
                            currentFromCard.number.takeLast(4).filter { it.isDigit() }.takeLast(4)
                        }
                        intent.putExtra("from_card_number", "•••• $fromCardLastFour")
                        val toCardNumber = when (selectedTransferSystem) {
                            "SWIFT" -> "$iban ($country)"
                            "Western Union", "Korona Pay" -> PhoneNumberFormatter.formatDigits(normalizedPhone)
                            else -> ""
                        }
                        intent.putExtra("to_card_number", toCardNumber)
                        intent.putExtra("description", description.takeIf { it.isNotEmpty() })
                        transferResponse.transactionId?.let {
                            intent.putExtra("transaction_id", it.toString())
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        val errorMessage = transferResponse.message ?: "Ошибка при переводе"
                        val intent = Intent(this@InternationalTransferActivity, TransferErrorActivity::class.java)
                        intent.putExtra("error_message", errorMessage)
                        startActivity(intent)
                    }
                }.onFailure { error ->
                    val intent = Intent(this@InternationalTransferActivity, TransferErrorActivity::class.java)
                    intent.putExtra("error_message", error.message ?: "Ошибка при переводе")
                    startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("InternationalTransfer", "Error performing transfer", e)
                val intent = Intent(this@InternationalTransferActivity, TransferErrorActivity::class.java)
                intent.putExtra("error_message", "Неожиданная ошибка: ${e.message}")
                startActivity(intent)
            }
        }
    }
}

