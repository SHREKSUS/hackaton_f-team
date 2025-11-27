package com.example.f_bank

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.api.FastForexClient
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.Card
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityCurrencyExchangeDialogBinding
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class CurrencyExchangeDialogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCurrencyExchangeDialogBinding
    private lateinit var userRepository: UserRepository
    private lateinit var securityPreferences: SecurityPreferences
    private var cards: List<Card> = emptyList()
    private var selectedCard: Card? = null
    private var selectedToCurrency: String? = null
    private var currencyRates: Map<String, Double> = emptyMap()
    private var kztRate: Double = 450.0 // Курс KZT к USD по умолчанию
    
    // Популярные валюты для обмена
    private val availableCurrencies = listOf("USD", "EUR", "GBP", "RUB", "CNY", "JPY", "KZT")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCurrencyExchangeDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userRepository = UserRepository(this)
        securityPreferences = SecurityPreferences(this)
        
        setupClickListeners()
        loadCards()
        loadCurrencyRates()
        setupAmountWatcher()
        setupAmountInput()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.cardFromCurrency.setOnClickListener {
            showCardSelectionDialog()
        }
        
        binding.cardToCurrency.setOnClickListener {
            showCurrencySelectionDialog()
        }
        
        binding.btnSwap.setOnClickListener {
            swapCurrencies()
        }
        
        binding.btnConfirmExchange.setOnClickListener {
            performExchange()
        }
    }
    
    private fun loadCards() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                val db = AppDatabase.getDatabase(this@CurrencyExchangeDialogActivity)
                val cardDao = db.cardDao()
                cards = cardDao.getCardsByUserId(userId)
                
                if (cards.isNotEmpty()) {
                    selectedCard = cards.first()
                    updateFromCardDisplay()
                }
            }
        }
    }
    
    private fun loadCurrencyRates() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    FastForexClient.apiService.getAllRates(
                        FastForexClient.getApiKey(),
                        "USD"
                    )
                }
                
                if (response.isSuccessful && response.body() != null) {
                    currencyRates = response.body()!!.results
                    kztRate = currencyRates["KZT"] ?: 450.0
                }
            } catch (e: Exception) {
                android.util.Log.e("CurrencyExchange", "Error loading rates", e)
            }
        }
    }
    
    private fun showCardSelectionDialog() {
        if (cards.isEmpty()) {
            Toast.makeText(this, "У вас нет карт", Toast.LENGTH_SHORT).show()
            return
        }
        
        val cardOptions = cards.map { card ->
            "•••• ${card.number.takeLast(4)} - ${formatBalance(card.balance)} ${card.currency}"
        }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Выберите карту")
            .setItems(cardOptions) { _, which ->
                selectedCard = cards[which]
                updateFromCardDisplay()
                calculateExchange()
            }
            .show()
    }
    
    private fun showCurrencySelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выберите валюту")
            .setItems(availableCurrencies.toTypedArray()) { _, which ->
                selectedToCurrency = availableCurrencies[which]
                updateToCurrencyDisplay()
                calculateExchange()
            }
            .show()
    }
    
    private fun updateFromCardDisplay() {
        selectedCard?.let { card ->
            binding.tvFromCurrencyCode.text = card.currency
            binding.tvFromCurrencyName.text = getCurrencyName(card.currency)
            binding.tvFromBalance.text = "Доступно: ${formatBalance(card.balance)} ${card.currency}"
        } ?: run {
            binding.tvFromCurrencyCode.text = "KZT"
            binding.tvFromCurrencyName.text = "Тенге"
            binding.tvFromBalance.text = "Доступно: 0.00 KZT"
        }
    }
    
    private fun updateToCurrencyDisplay() {
        selectedToCurrency?.let { currency ->
            binding.tvToCurrencyCode.text = currency
            binding.tvToCurrencyName.text = getCurrencyName(currency)
            val fromCurrency = selectedCard?.currency ?: "KZT"
            val rate = calculateExchangeRate(fromCurrency, currency)
            val baseRate = if (fromCurrency == "KZT") {
                kztRate / (if (currency == "USD") 1.0 else (currencyRates[currency] ?: 1.0))
            } else {
                (currencyRates[fromCurrency] ?: 1.0) / (if (currency == "USD") 1.0 else (currencyRates[currency] ?: 1.0))
            }
            binding.tvToCurrencyRate.text = "Курс: 1 $currency = ${String.format("%.2f", 1.0 / baseRate)} $fromCurrency"
        } ?: run {
            binding.tvToCurrencyCode.text = "USD"
            binding.tvToCurrencyName.text = "Доллар США"
            binding.tvToCurrencyRate.text = "Курс: 1 USD = 450 KZT"
        }
    }
    
    private fun swapCurrencies() {
        val tempCurrency = selectedToCurrency
        selectedToCurrency = selectedCard?.currency
        selectedCard?.let { card ->
            // Находим карту с валютой, на которую хотим обменять
            val newCard = cards.find { it.currency == tempCurrency }
            if (newCard != null) {
                selectedCard = newCard
            }
        }
        updateFromCardDisplay()
        updateToCurrencyDisplay()
        calculateExchange()
    }
    
    private fun setupAmountInput() {
        // Убеждаемся, что поле ввода может получить фокус
        binding.etAmount.isFocusable = true
        binding.etAmount.isFocusableInTouchMode = true
        binding.etAmount.isClickable = true
        
        // Устанавливаем минимальную высоту для лучшего UX
        binding.etAmount.minHeight = (56 * resources.displayMetrics.density).toInt()
    }
    
    private fun setupAmountWatcher() {
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateExchange()
            }
        })
    }
    
    private fun calculateExchange() {
        val amountText = binding.etAmount.text.toString()
        if (amountText.isBlank() || selectedCard == null || selectedToCurrency == null) {
            binding.tvResultAmount.text = "0.00"
            binding.tvExchangeRate.text = ""
            return
        }
        
        val amount = amountText.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            binding.tvResultAmount.text = "0.00"
            binding.tvExchangeRate.text = ""
            return
        }
        
        val fromCurrency = selectedCard!!.currency
        val toCurrency = selectedToCurrency!!
        
        if (fromCurrency == toCurrency) {
            binding.tvResultAmount.text = formatBalance(amount)
            binding.tvExchangeRate.text = "Курс: 1.0"
            return
        }
        
        val exchangeRate = calculateExchangeRate(fromCurrency, toCurrency)
        val resultAmount = amount * exchangeRate
        
        val numberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
        numberFormat.maximumFractionDigits = 2
        numberFormat.minimumFractionDigits = 2
        
        binding.tvResultAmount.text = "${numberFormat.format(resultAmount)} $toCurrency"
        
        // Обновляем курс обмена для отображения
        val displayRate = calculateExchangeRate(fromCurrency, toCurrency)
        // Показываем курс в формате: 1 toCurrency = X fromCurrency
        val reverseRate = 1.0 / displayRate
        binding.tvExchangeRate.text = "1 $toCurrency = ${String.format("%.2f", reverseRate)} $fromCurrency"
        
        // Обновляем комиссию
        binding.tvCommission.text = "2%"
    }
    
    private fun calculateExchangeRate(fromCurrency: String, toCurrency: String): Double {
        // Если обе валюты одинаковые
        if (fromCurrency == toCurrency) return 1.0
        
        // Fast Forex API возвращает курсы относительно USD (base = USD)
        // currencyRates["KZT"] = 450 означает 1 USD = 450 KZT
        // currencyRates["EUR"] = 0.91 означает 1 USD = 0.91 EUR
        
        // Получаем курс fromCurrency к USD
        val fromRateToUsd = when (fromCurrency) {
            "USD" -> 1.0
            "KZT" -> kztRate // 1 USD = 450 KZT, значит 1 KZT = 1/450 USD
            else -> currencyRates[fromCurrency] ?: return 1.0
        }
        
        // Получаем курс toCurrency к USD
        val toRateToUsd = when (toCurrency) {
            "USD" -> 1.0
            "KZT" -> kztRate
            else -> currencyRates[toCurrency] ?: return 1.0
        }
        
        // Конвертируем fromCurrency -> USD -> toCurrency
        // Если 1 USD = fromRateToUsd fromCurrency, то 1 fromCurrency = 1/fromRateToUsd USD
        // Если 1 USD = toRateToUsd toCurrency, то 1 USD = toRateToUsd toCurrency
        // Значит: 1 fromCurrency = (1/fromRateToUsd) USD = (1/fromRateToUsd) * toRateToUsd toCurrency
        
        // Пример: 1 KZT -> USD -> EUR
        // 1 KZT = 1/450 USD
        // 1 USD = 0.91 EUR
        // 1 KZT = (1/450) * 0.91 EUR = 0.00202 EUR
        
        val baseRate = toRateToUsd / fromRateToUsd
        
        // Применяем спред банка (2% комиссия)
        // Банк берет комиссию, поэтому клиент получает меньше
        return baseRate * 0.98
    }
    
    private fun performExchange() {
        val amountText = binding.etAmount.text.toString()
        val amount = amountText.toDoubleOrNull()
        
        if (selectedCard == null) {
            Toast.makeText(this, "Выберите карту", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedToCurrency == null) {
            Toast.makeText(this, "Выберите валюту для обмена", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (amount > selectedCard!!.balance) {
            Toast.makeText(this, "Недостаточно средств на карте", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fromCurrency = selectedCard!!.currency
        val toCurrency = selectedToCurrency!!
        
        if (fromCurrency == toCurrency) {
            Toast.makeText(this, "Выберите другую валюту", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.exchangeCurrency(
                    userId = userId,
                    fromCardId = selectedCard!!.id,
                    fromCurrency = fromCurrency,
                    toCurrency = toCurrency,
                    amount = amount
                )
                    .onSuccess {
                        Toast.makeText(this@CurrencyExchangeDialogActivity, "Обмен выполнен успешно", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .onFailure { error ->
                        Toast.makeText(this@CurrencyExchangeDialogActivity, "Ошибка обмена: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
    
    private fun formatBalance(balance: Double): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
        numberFormat.maximumFractionDigits = 2
        numberFormat.minimumFractionDigits = 2
        return numberFormat.format(balance)
    }
    
    private fun getCurrencyName(currencyCode: String): String {
        return when (currencyCode) {
            "USD" -> "Доллар США"
            "EUR" -> "Евро"
            "GBP" -> "Фунт стерлингов"
            "RUB" -> "Российский рубль"
            "CNY" -> "Китайский юань"
            "JPY" -> "Японская йена"
            "KZT" -> "Казахстанский тенге"
            else -> currencyCode
        }
    }
}

