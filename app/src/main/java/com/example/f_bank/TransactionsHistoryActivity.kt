package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.Transaction
import com.example.f_bank.data.TransactionType
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityTransactionsHistoryBinding
import com.example.f_bank.ui.GroupedTransactionAdapter
import android.text.Editable
import android.text.TextWatcher
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class TransactionsHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransactionsHistoryBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var adapter: GroupedTransactionAdapter
    private var allTransactions: List<Transaction> = emptyList()
    private var filteredTransactions: List<Transaction> = emptyList()
    private var currentFilter: TransactionFilter = TransactionFilter.ALL
    private var searchQuery: String = ""

    enum class TransactionFilter {
        ALL, SENT, RECEIVED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionsHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)

        setupRecyclerView()
        setupClickListeners()
        setupFilterButtons()
        setupSearch()
        loadTransactions()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Клик на карточку расходов - открываем аналитику с расходами
        binding.cardExpenses.setOnClickListener {
            openAnalytics(showExpenses = true)
        }
        
        // Клик на карточку доходов - открываем аналитику с доходами
        binding.cardIncomes.setOnClickListener {
            openAnalytics(showExpenses = false)
        }
        
        // Клик на карточку баланса - открываем аналитику (по умолчанию показываем расходы)
        binding.cardBalance.setOnClickListener {
            openAnalytics(showExpenses = true)
        }
    }
    
    private fun openAnalytics(showExpenses: Boolean) {
        val intent = Intent(this, TransactionsAnalyticsActivity::class.java).apply {
            putExtra("show_expenses", showExpenses)
        }
        startActivity(intent)
    }

    private fun setupRecyclerView() {
        adapter = GroupedTransactionAdapter(emptyList())
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter
    }

    private fun setupFilterButtons() {
        binding.btnAll.setOnClickListener { applyFilter(TransactionFilter.ALL) }
        binding.btnSent.setOnClickListener { applyFilter(TransactionFilter.SENT) }
        binding.btnReceived.setOnClickListener { applyFilter(TransactionFilter.RECEIVED) }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                applyFiltersAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadTransactions() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                try {
                    withContext(Dispatchers.IO) {
                        // Проверяем, есть ли у пользователя карты
                        val cardDao = AppDatabase.getDatabase(this@TransactionsHistoryActivity).cardDao()
                        val userCards = cardDao.getCardsByUserId(userId)
                        
                        // Если карт нет, не показываем транзакции
                        if (userCards.isEmpty()) {
                            allTransactions = emptyList()
                            return@withContext
                        }
                        
                        // Загружаем транзакции из локальной БД
                        val transactionDao = AppDatabase.getDatabase(this@TransactionsHistoryActivity).transactionDao()
                        val transactionEntities = transactionDao.getTransactionsByUserId(userId, limit = 1000)
                        
                        allTransactions = transactionEntities.mapNotNull { it.toTransaction() }
                    }
                    updateSummaryCards()
                    applyFiltersAndSearch()
                } catch (e: Exception) {
                    android.util.Log.e("TransactionsHistoryActivity", "Error loading transactions", e)
                    allTransactions = emptyList()
                    updateSummaryCards()
                    applyFiltersAndSearch()
                }
            } else {
                allTransactions = emptyList()
                updateSummaryCards()
                applyFiltersAndSearch()
            }
        }
    }
    
    private fun updateSummaryCards() {
        // Получаем текущий месяц
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        
        // Устанавливаем начало месяца
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // Устанавливаем конец месяца
        val endOfMonth = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        
        // Фильтруем транзакции за текущий месяц
        val monthTransactions = allTransactions.filter { transaction ->
            transaction.timestamp >= startOfMonth.timeInMillis && 
            transaction.timestamp <= endOfMonth.timeInMillis
        }
        
        // Разделяем на доходы и расходы
        val expenses = monthTransactions.filter { 
            it.amount < 0 || (it.type == TransactionType.TRANSFER_OUT && it.amount > 0)
        }
        val incomes = monthTransactions.filter { 
            it.amount > 0 && it.type != TransactionType.TRANSFER_OUT
        }
        
        val totalExpenses = abs(expenses.sumOf { it.amount })
        val totalIncomes = incomes.sumOf { it.amount }
        val balance = totalIncomes - totalExpenses
        
        // Форматируем числа
        val numberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
        numberFormat.maximumFractionDigits = 0
        
        // Обновляем UI
        binding.tvExpensesAmount.text = numberFormat.format(totalExpenses)
        binding.tvIncomesAmount.text = numberFormat.format(totalIncomes)
        
        // Баланс может быть отрицательным, показываем знак
        val balanceText = if (balance >= 0) {
            "+${numberFormat.format(balance)}"
        } else {
            numberFormat.format(balance)
        }
        binding.tvBalanceAmount.text = balanceText
        
        // Обновляем период (название месяца)
        val monthNames = arrayOf(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
        )
        val monthName = monthNames[currentMonth]
        binding.tvExpensesPeriod.text = "За $monthName"
    }

    private fun applyFiltersAndSearch() {
        // Применяем фильтр
        var filtered = when (currentFilter) {
            TransactionFilter.ALL -> allTransactions
            TransactionFilter.SENT -> allTransactions.filter { 
                it.type == TransactionType.TRANSFER_OUT || 
                (it.type == TransactionType.PURCHASE && it.amount < 0)
            }
            TransactionFilter.RECEIVED -> allTransactions.filter { 
                it.type == TransactionType.TRANSFER_IN || 
                it.type == TransactionType.DEPOSIT ||
                (it.amount > 0 && it.type != TransactionType.TRANSFER_OUT)
            }
        }
        
        // Применяем поиск
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { transaction ->
                transaction.title.contains(searchQuery, ignoreCase = true) ||
                kotlin.math.abs(transaction.amount).toString().contains(searchQuery, ignoreCase = true)
            }
        }
        
        // Сортируем по дате (новые сначала)
        filtered = filtered.sortedByDescending { it.timestamp }
        
        filteredTransactions = filtered
        adapter.updateTransactions(filtered)
    }

    private fun applyFilter(filter: TransactionFilter) {
        currentFilter = filter
        applyFiltersAndSearch()
        updateFilterButtons(filter)
    }

    private fun updateFilterButtons(selectedFilter: TransactionFilter) {
        binding.btnAll.isSelected = selectedFilter == TransactionFilter.ALL
        binding.btnSent.isSelected = selectedFilter == TransactionFilter.SENT
        binding.btnReceived.isSelected = selectedFilter == TransactionFilter.RECEIVED
        
        // Обновляем стили кнопок
        val selectedStyle = com.google.android.material.R.attr.materialButtonOutlinedStyle
        val unselectedStyle = com.google.android.material.R.attr.materialButtonOutlinedStyle
        
        binding.btnAll.setBackgroundColor(
            if (selectedFilter == TransactionFilter.ALL) 
                getColor(R.color.button_primary_bg) 
            else 
                android.graphics.Color.TRANSPARENT
        )
        binding.btnAll.setTextColor(
            if (selectedFilter == TransactionFilter.ALL) 
                getColor(R.color.white) 
            else 
                getColor(R.color.text_secondary)
        )
        
        binding.btnSent.setBackgroundColor(
            if (selectedFilter == TransactionFilter.SENT) 
                getColor(R.color.button_primary_bg) 
            else 
                android.graphics.Color.TRANSPARENT
        )
        binding.btnSent.setTextColor(
            if (selectedFilter == TransactionFilter.SENT) 
                getColor(R.color.white) 
            else 
                getColor(R.color.text_secondary)
        )
        
        binding.btnReceived.setBackgroundColor(
            if (selectedFilter == TransactionFilter.RECEIVED) 
                getColor(R.color.button_primary_bg) 
            else 
                android.graphics.Color.TRANSPARENT
        )
        binding.btnReceived.setTextColor(
            if (selectedFilter == TransactionFilter.RECEIVED) 
                getColor(R.color.white) 
            else 
                getColor(R.color.text_secondary)
        )
    }


}

