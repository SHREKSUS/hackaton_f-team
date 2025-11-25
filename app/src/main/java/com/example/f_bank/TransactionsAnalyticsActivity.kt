package com.example.f_bank

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.Transaction
import com.example.f_bank.data.TransactionCategory
import com.example.f_bank.databinding.ActivityTransactionsAnalyticsBinding
import com.example.f_bank.databinding.DialogCustomPeriodBinding
import com.example.f_bank.ui.CategoryAnalyticsAdapter
import com.example.f_bank.ui.CategoryAnalyticsItem
import com.example.f_bank.utils.PeriodHelper
import com.example.f_bank.utils.QuickPeriodType
import com.example.f_bank.utils.SecurityPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TransactionsAnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransactionsAnalyticsBinding
    private lateinit var securityPreferences: SecurityPreferences
    private var allTransactions: List<Transaction> = emptyList()
    private var isShowingExpenses = false // false = доходы, true = расходы
    private var currentQuickPeriod: QuickPeriodType? = QuickPeriodType.CURRENT_MONTH
    private var customPeriodStart: Calendar? = null
    private var customPeriodEnd: Calendar? = null
    private var isCustomPeriod = false
    
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru-RU"))
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionsAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        securityPreferences = SecurityPreferences(this)
        
        // Получаем параметр из Intent, если он передан
        val showExpensesFromIntent = intent.getBooleanExtra("show_expenses", false)
        if (showExpensesFromIntent) {
            isShowingExpenses = true
        }
        
        setupQuickPeriods()
        setupClickListeners()
        updatePeriodDisplay()
        updateToggleButtons()
        loadAnalytics()
    }
    
    private fun setupQuickPeriods() {
        val quickPeriods = listOf(
            QuickPeriodType.TODAY to "Сегодня",
            QuickPeriodType.YESTERDAY to "Вчера",
            QuickPeriodType.LAST_7_DAYS to "7 дней",
            QuickPeriodType.CURRENT_MONTH to "Месяц",
            QuickPeriodType.LAST_MONTH to "Прошлый месяц",
            QuickPeriodType.CURRENT_YEAR to "Год",
            QuickPeriodType.LAST_YEAR to "Прошлый год"
        )
        
        quickPeriods.forEach { (period, label) ->
            val button = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                val margin8dp = (8 * resources.displayMetrics.density).toInt()
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = margin8dp
                layoutParams = params
                setOnClickListener {
                    selectQuickPeriod(period)
                }
            }
            binding.quickPeriodsLayout.addView(button)
        }
        
        // Добавляем кнопку "Произвольный период" в тот же контейнер
        val customPeriodButton = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Произвольный период"
            val margin8dp = (8 * resources.displayMetrics.density).toInt()
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = margin8dp
            layoutParams = params
            setOnClickListener {
                showCustomPeriodDialog()
            }
        }
        binding.quickPeriodsLayout.addView(customPeriodButton)
        
        // Выделяем текущий период
        updateQuickPeriodButtons()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnExpenses.setOnClickListener {
            isShowingExpenses = true
            updateToggleButtons()
            loadAnalytics()
        }
        
        binding.btnIncomes.setOnClickListener {
            isShowingExpenses = false
            updateToggleButtons()
            loadAnalytics()
        }
    }
    
    private fun selectQuickPeriod(period: QuickPeriodType) {
        currentQuickPeriod = period
        isCustomPeriod = false
        customPeriodStart = null
        customPeriodEnd = null
        updateQuickPeriodButtons()
        updatePeriodDisplay()
        loadAnalytics()
    }
    
    private fun updateQuickPeriodButtons() {
        for (i in 0 until binding.quickPeriodsLayout.childCount) {
            val button = binding.quickPeriodsLayout.getChildAt(i) as? com.google.android.material.button.MaterialButton
            button?.let {
                val period = getPeriodForButton(i)
                val isSelected = !isCustomPeriod && currentQuickPeriod == period
                
                if (isSelected) {
                    it.setBackgroundColor(getColor(com.example.f_bank.R.color.button_primary_bg))
                    it.setTextColor(getColor(com.example.f_bank.R.color.button_primary_text))
                } else {
                    it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    it.setTextColor(getColor(com.example.f_bank.R.color.text_primary))
                }
            }
        }
    }
    
    private fun getPeriodForButton(index: Int): QuickPeriodType {
        return when (index) {
            0 -> QuickPeriodType.TODAY
            1 -> QuickPeriodType.YESTERDAY
            2 -> QuickPeriodType.LAST_7_DAYS
            3 -> QuickPeriodType.CURRENT_MONTH
            4 -> QuickPeriodType.LAST_MONTH
            5 -> QuickPeriodType.CURRENT_YEAR
            6 -> QuickPeriodType.LAST_YEAR
            else -> QuickPeriodType.CURRENT_MONTH
        }
    }
    
    private fun showCustomPeriodDialog() {
        val view = LayoutInflater.from(this).inflate(com.example.f_bank.R.layout.dialog_custom_period, null)
        val btnStartDate = view.findViewById<com.google.android.material.button.MaterialButton>(com.example.f_bank.R.id.btnStartDate)
        val btnEndDate = view.findViewById<com.google.android.material.button.MaterialButton>(com.example.f_bank.R.id.btnEndDate)
        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(com.example.f_bank.R.id.btnCancel)
        val btnApply = view.findViewById<com.google.android.material.button.MaterialButton>(com.example.f_bank.R.id.btnApply)
        
        val startDate = customPeriodStart ?: Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val endDate = customPeriodEnd ?: Calendar.getInstance()
        
        btnStartDate.text = dateFormat.format(startDate.time)
        btnEndDate.text = dateFormat.format(endDate.time)
        
        btnStartDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    startDate.set(year, month, dayOfMonth)
                    btnStartDate.text = dateFormat.format(startDate.time)
                },
                startDate.get(Calendar.YEAR),
                startDate.get(Calendar.MONTH),
                startDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        
        btnEndDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    endDate.set(year, month, dayOfMonth)
                    btnEndDate.text = dateFormat.format(endDate.time)
                },
                endDate.get(Calendar.YEAR),
                endDate.get(Calendar.MONTH),
                endDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        
        val dialog = AlertDialog.Builder(this, com.example.f_bank.R.style.AlertDialogTheme)
            .setView(view)
            .setCancelable(true)
            .create()
        
        // Настраиваем размеры и отступы диалога
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnApply.setOnClickListener {
            if (startDate.timeInMillis > endDate.timeInMillis) {
                Toast.makeText(this, "Начальная дата не может быть больше конечной", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            customPeriodStart = startDate.clone() as Calendar
            customPeriodEnd = endDate.clone() as Calendar
            isCustomPeriod = true
            currentQuickPeriod = null
            updateQuickPeriodButtons()
            updatePeriodDisplay()
            loadAnalytics()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun updatePeriodDisplay() {
        val periodText = if (isCustomPeriod && customPeriodStart != null && customPeriodEnd != null) {
            val start = customPeriodStart!!.timeInMillis
            val end = customPeriodEnd!!.timeInMillis
            PeriodHelper.formatPeriodDisplay(start, end)
        } else if (currentQuickPeriod != null) {
            val (start, end) = PeriodHelper.getQuickPeriodRange(currentQuickPeriod!!)
            PeriodHelper.formatPeriodDisplay(start, end)
        } else {
            "Период не выбран"
        }
        
        val title = if (isShowingExpenses) "Аналитика расходов" else "Аналитика доходов"
        binding.tvPeriodDisplay.text = "$title • $periodText"
    }
    
    private fun updateToggleButtons() {
        if (isShowingExpenses) {
            binding.btnExpenses.setBackgroundColor(getColor(com.example.f_bank.R.color.button_primary_bg))
            binding.btnExpenses.setTextColor(getColor(com.example.f_bank.R.color.white))
            binding.btnIncomes.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnIncomes.setTextColor(getColor(com.example.f_bank.R.color.text_secondary))
            binding.tvCategoriesTitle.text = "Расходы по категориям"
        } else {
            binding.btnIncomes.setBackgroundColor(getColor(com.example.f_bank.R.color.button_primary_bg))
            binding.btnIncomes.setTextColor(getColor(com.example.f_bank.R.color.white))
            binding.btnExpenses.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.btnExpenses.setTextColor(getColor(com.example.f_bank.R.color.text_secondary))
            binding.tvCategoriesTitle.text = "Доходы по категориям"
        }
        updatePeriodDisplay()
    }
    
    private fun loadAnalytics() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId == null) {
                Toast.makeText(this@TransactionsAnalyticsActivity, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            allTransactions = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val transactionDao = AppDatabase.getDatabase(this@TransactionsAnalyticsActivity).transactionDao()
                transactionDao.getAllTransactionsByUserId(userId)
                    .mapNotNull { it.toTransaction() }
            }
            
            if (allTransactions.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.donutChart.visibility = View.GONE
                binding.cardBalance.visibility = View.GONE
                binding.tvCategoriesTitle.visibility = View.GONE
                binding.rvCategories.visibility = View.GONE
                return@launch
            }
            
            binding.tvEmpty.visibility = View.GONE
            binding.donutChart.visibility = View.VISIBLE
            binding.cardBalance.visibility = View.VISIBLE
            binding.tvCategoriesTitle.visibility = View.VISIBLE
            binding.rvCategories.visibility = View.VISIBLE
            
            // Фильтруем транзакции по выбранному периоду
            val (startTime, endTime) = if (isCustomPeriod && customPeriodStart != null && customPeriodEnd != null) {
                val start = customPeriodStart!!.clone() as Calendar
                val end = customPeriodEnd!!.clone() as Calendar
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
                Pair(start.timeInMillis, end.timeInMillis)
            } else if (currentQuickPeriod != null) {
                PeriodHelper.getQuickPeriodRange(currentQuickPeriod!!)
            } else {
                val now = Calendar.getInstance()
                val start = Calendar.getInstance().apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                Pair(start.timeInMillis, now.timeInMillis)
            }
            
            val filteredTransactions = allTransactions.filter { transaction ->
                transaction.timestamp >= startTime && transaction.timestamp <= endTime
            }
            
            // Разделяем на доходы и расходы
            val expenses = filteredTransactions.filter { 
                it.amount < 0 || (it.type == com.example.f_bank.data.TransactionType.TRANSFER_OUT && it.amount > 0)
            }
            val incomes = filteredTransactions.filter { 
                it.amount > 0 && it.type != com.example.f_bank.data.TransactionType.TRANSFER_OUT
            }
            
            val totalExpenses = abs(expenses.sumOf { it.amount })
            val totalIncomes = incomes.sumOf { it.amount }
            val balance = totalIncomes - totalExpenses
            
            // Обновляем карточки
            val numberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
            numberFormat.maximumFractionDigits = 0
            
            binding.tvIncomeAmount.text = numberFormat.format(totalIncomes)
            binding.tvExpenseAmount.text = numberFormat.format(totalExpenses)
            
            val balanceText = if (balance >= 0) "+${numberFormat.format(balance)} Р" else "${numberFormat.format(balance)} Р"
            binding.tvBalanceAmount.text = balanceText
            
            // Отображаем данные в зависимости от выбранного режима
            if (isShowingExpenses) {
                displayExpenses(expenses, totalExpenses)
            } else {
                displayIncomes(incomes, totalIncomes)
            }
        }
    }
    
    private fun displayExpenses(expenses: List<Transaction>, totalExpenses: Double) {
        if (totalExpenses == 0.0) {
            binding.donutChart.visibility = View.GONE
            binding.rvCategories.visibility = View.GONE
            return
        }
        
        // Группируем по категориям
        val expenseByCategory = expenses
            .groupBy { it.category ?: TransactionCategory.OTHER_EXPENSE }
            .mapValues { (_, trans) -> abs(trans.sumOf { it.amount }) }
            .toList()
            .sortedByDescending { it.second }
        
        // Отображаем donut chart
        binding.donutChart.setData(
            expenseByCategory,
            totalExpenses,
            "Всего расходов",
            isExpenses = true
        )
        
        // Отображаем категории
        val expenseColors = listOf(
            0xFF000000.toInt(), // Черный
            0xFF999999.toInt(), // Серый
            0xFF333333.toInt(), // Темно-серый
            0xFFCCCCCC.toInt(), // Светло-серый
            0xFF666666.toInt(), // Средне-серый
        )
        
        val categoryItems = expenseByCategory.mapIndexed { index, (category, amount) ->
            CategoryAnalyticsItem(
                category = category,
                amount = amount,
                percentage = (amount / totalExpenses * 100),
                iconRes = getCategoryIcon(category),
                color = expenseColors[index % expenseColors.size]
            )
        }
        
        val adapter = CategoryAnalyticsAdapter(categoryItems)
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = adapter
    }
    
    private fun displayIncomes(incomes: List<Transaction>, totalIncomes: Double) {
        if (totalIncomes == 0.0) {
            binding.donutChart.visibility = View.GONE
            binding.rvCategories.visibility = View.GONE
            return
        }
        
        // Группируем по категориям
        val incomeByCategory = incomes
            .groupBy { it.category ?: TransactionCategory.INCOME_DEPOSIT }
            .mapValues { (_, trans) -> trans.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
        
        // Отображаем donut chart
        binding.donutChart.setData(
            incomeByCategory,
            totalIncomes,
            "Всего доходов",
            isExpenses = false
        )
        
        // Отображаем категории
        val incomeColors = listOf(
            0xFF000000.toInt(), // Черный
            0xFF333333.toInt(), // Темно-серый
            0xFF666666.toInt(), // Серый
            0xFF999999.toInt(), // Светло-серый
        )
        
        val categoryItems = incomeByCategory.mapIndexed { index, (category, amount) ->
            CategoryAnalyticsItem(
                category = category,
                amount = amount,
                percentage = (amount / totalIncomes * 100),
                iconRes = getCategoryIcon(category),
                color = incomeColors[index % incomeColors.size]
            )
        }
        
        val adapter = CategoryAnalyticsAdapter(categoryItems)
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = adapter
    }
    
    private fun getCategoryIcon(category: TransactionCategory): Int {
        return when {
            category.name.contains("SALARY", ignoreCase = true) -> com.example.f_bank.R.drawable.ic_wallet
            category.name.contains("FREELANCE", ignoreCase = true) -> com.example.f_bank.R.drawable.ic_wallet
            category.name.contains("INVESTMENT", ignoreCase = true) -> com.example.f_bank.R.drawable.ic_trending_up
            else -> com.example.f_bank.R.drawable.ic_wallet
        }
    }
}
