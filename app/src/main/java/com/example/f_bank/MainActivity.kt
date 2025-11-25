package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.R
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.Card
import com.example.f_bank.data.Transaction
import com.example.f_bank.data.TransactionEntity
import com.example.f_bank.data.TransactionType
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityMainBinding
import com.example.f_bank.ui.GroupedTransactionAdapter
import com.example.f_bank.ui.CardAdapter
import com.example.f_bank.ui.CompactCardAdapter
import com.example.f_bank.utils.CardEncryption
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.utils.onFailure
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import androidx.appcompat.app.AlertDialog
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private var isBalanceVisible = false // По умолчанию баланс скрыт
    private lateinit var compactCardAdapter: CompactCardAdapter
    private var mainCard: Card? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)

        setupRecyclerView()
        setupCardsRecyclerView()
        setupClickListeners()
        updateBalanceVisibility()
        
        // Инициализируем RecyclerView с пустым списком, если транзакции еще не загружены
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = GroupedTransactionAdapter(emptyList())
        
        // Загружаем данные при создании активности
        loadUserData()
        loadTransactions()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Обновляем intent при использовании FLAG_ACTIVITY_CLEAR_TOP
        setIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Проверяем флаги обновления из Intent
        val refreshCards = intent.getBooleanExtra("refresh_cards", false)
        val refreshTransactions = intent.getBooleanExtra("refresh_transactions", false)
        val skipSync = intent.getBooleanExtra("skip_sync", false)
        
        // Сначала загружаем данные из локальной БД для быстрого отображения
        loadUserData()
        // Загружаем транзакции из БД
        loadTransactions()
        
        // Если был перевод, принудительно перезагружаем карты и транзакции из БД
        if (refreshCards || refreshTransactions) {
            android.util.Log.d("MainActivity", "Refreshing data after transfer: cards=$refreshCards, transactions=$refreshTransactions")
            // Небольшая задержка для гарантии, что данные обновлены в БД
            lifecycleScope.launch {
                kotlinx.coroutines.delay(300)
                // Перезагружаем карты
                if (refreshCards) {
                    loadUserData()
                }
                // Перезагружаем транзакции
                if (refreshTransactions) {
                    loadTransactions()
                }
            }
            // Очищаем флаги после использования
            intent.removeExtra("refresh_cards")
            intent.removeExtra("refresh_transactions")
        }
        
        // Синхронизируем карты с сервером только если не было недавнего перевода
        // (чтобы не перезаписывать локально обновленные балансы)
        if (!skipSync) {
            syncCardsFromServer()
        } else {
            // Очищаем флаг для следующего запуска
            intent.removeExtra("skip_sync")
            android.util.Log.d("MainActivity", "Skipping sync after transfer")
        }
    }
    
    private fun syncCardsFromServer() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            val token = securityPreferences.getAuthToken()
            if (userId != null && token != null) {
                // Синхронизируем карты с сервером и обновляем UI после завершения
                userRepository.syncCardsFromServer(userId, token)
                    .onSuccess {
                        // После успешной синхронизации перезагружаем карты для отображения обновленных балансов
                        loadUserData()
                    }
                    .onFailure {
                        // При ошибке данные уже загружены из локальной БД
                    }
            }
        }
    }

    private fun setupRecyclerView() {
        // Загружаем транзакции из БД
        loadTransactions()
    }
    
    private fun loadTransactions() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        // Проверяем, есть ли у пользователя карты
                        val cardDao = AppDatabase.getDatabase(this@MainActivity).cardDao()
                        val userCards = cardDao.getCardsByUserId(userId)
                        
                        // Если карт нет, не показываем транзакции
                        if (userCards.isEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val adapter = GroupedTransactionAdapter(emptyList())
                            binding.rvTransactions.layoutManager = LinearLayoutManager(this@MainActivity)
                            binding.rvTransactions.adapter = adapter
                        }
                        return@withContext
                        }
                        
                        val transactionDao = AppDatabase.getDatabase(this@MainActivity).transactionDao()
                        val transactionEntities = transactionDao.getTransactionsByUserId(userId, limit = 10)
                        android.util.Log.d("MainActivity", "Loaded ${transactionEntities.size} transactions from DB for userId=$userId")
                        transactionEntities.forEach { tx ->
                            android.util.Log.d("MainActivity", "Transaction: id=${tx.id}, title=${tx.title}, amount=${tx.amount}, type=${tx.type}")
                        }
                        val transactions = transactionEntities.mapNotNull { 
                            val tx = it.toTransaction()
                            if (tx != null) {
                                android.util.Log.d("MainActivity", "Converted transaction: ${tx.title}, amount=${tx.amount}")
                            } else {
                                android.util.Log.w("MainActivity", "Failed to convert transaction: type=${it.type}")
                            }
                            tx
                        }
                        
                        // Если транзакций нет, показываем пустой список (не примеры)
                        // Сортируем транзакции по дате (новые сначала) для главной страницы
                        val finalTransactions = transactions.sortedByDescending { it.timestamp }
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val adapter = GroupedTransactionAdapter(finalTransactions)
                            binding.rvTransactions.layoutManager = LinearLayoutManager(this@MainActivity)
                            binding.rvTransactions.adapter = adapter
                        }
                    }
                } catch (e: Exception) {
                    // В случае ошибки показываем пустой список
                    android.util.Log.e("MainActivity", "Error loading transactions", e)
                    val adapter = GroupedTransactionAdapter(emptyList())
                    binding.rvTransactions.layoutManager = LinearLayoutManager(this@MainActivity)
                    binding.rvTransactions.adapter = adapter
                }
            } else {
                // Если пользователь не авторизован, показываем пустой список
                val adapter = GroupedTransactionAdapter(emptyList())
                binding.rvTransactions.layoutManager = LinearLayoutManager(this@MainActivity)
                binding.rvTransactions.adapter = adapter
            }
        }
    }

    private fun setupCardsRecyclerView(userName: String = "") {
        // Настраиваем вертикальный список для остальных карт
        compactCardAdapter = CompactCardAdapter(
            cards = emptyList(),
            onCardClick = { card ->
                // При клике на карту в списке она становится главной
                setMainCard(card, userName)
            }
        )
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.rvOtherCards.layoutManager = layoutManager
        binding.rvOtherCards.adapter = compactCardAdapter
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                // Загружаем имя пользователя из локальной БД
                // Имя обновляется при каждом логине с сервера
                val user = userRepository.getUserById(userId)
                user?.let {
                    // Форматируем ФИО: показываем "Фамилия И."
                    val nameParts = it.name.split(" ")
                    val displayName = when {
                        nameParts.size >= 2 -> {
                            // Фамилия Имя (Отчество) -> Фамилия И.
                            "${nameParts[0]} ${nameParts[1].firstOrNull()?.uppercaseChar()}."
                        }
                        nameParts.isNotEmpty() -> nameParts[0]
                        else -> it.name
                    }
                    binding.tvUserName.text = displayName
                    android.util.Log.d("MainActivity", "User name loaded: ${it.name} -> $displayName")
                }
                
                // Загружаем карты пользователя
                loadCards(userId)
            }
        }
    }
    
    private fun loadCards(userId: Long) {
        lifecycleScope.launch {
            val result = userRepository.getCards(userId)
            result.onSuccess { cards ->
                // Логируем балансы для отладки
                cards.forEach { card ->
                    android.util.Log.d("MainActivity", "Card ${card.id}: balance=${card.balance}, number=••••${card.number.takeLast(4)}")
                }
                
                // Вычисляем суммарный баланс всех карт (независимо от видимости)
                val totalBalance = cards.sumOf { it.balance }
                android.util.Log.d("MainActivity", "Total balance: $totalBalance")
                
                // Обновляем общий баланс
                displayCardInBalance(totalBalance)
                
                // Отображаем только видимые карты в горизонтальном скролле, отсортированные по порядку
                val visibleCards = cards.filter { !it.isHidden }.sortedBy { it.displayOrder }
                if (visibleCards.isEmpty()) {
                    // Если карт нет, показываем карточку создания по центру
                    showEmptyCardState()
                } else {
                    // Если есть карты, показываем их в горизонтальном скролле
                    hideEmptyCardState()
                    displayAllCards(visibleCards)
                }
            }.onFailure {
                // При ошибке показываем пустой список с кнопкой создания
                android.util.Log.e("MainActivity", "Error loading cards", it)
                showEmptyCardState()
                displayCardInBalance(0.0)
            }
        }
    }
    
    private fun displayCardInBalance(totalBalance: Double) {
        // Обновляем общий баланс (сумма всех карт)
        updateTotalBalance(totalBalance)
        
        // Делаем карту кликабельной для управления картами
        binding.cardBalance.setOnClickListener {
            val intent = Intent(this, ManageCardsActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun updateTotalBalance(totalBalance: Double) {
        // Обновляем общий баланс (всегда обновляем значение, даже если скрыт)
        if (isBalanceVisible) {
            binding.tvBalance.text = formatBalance(totalBalance)
        } else {
            binding.tvBalance.text = getString(R.string.balance_hidden)
        }
    }

    private fun displayAllCards(cards: List<Card>) {
        // Получаем имя пользователя для карт
        val userId = securityPreferences.getCurrentUserId()
        if (userId != null) {
            lifecycleScope.launch {
                val user = userRepository.getUserById(userId)
                android.util.Log.d("MainActivity", "Loaded user: id=$userId, name=${user?.name}")
                
                // Форматируем ФИО для карт: показываем "ФАМИЛИЯ И." в верхнем регистре
                val nameParts = user?.name?.split(" ") ?: emptyList()
                val userName = if (nameParts.size >= 2 && user?.name?.isNotBlank() == true) {
                    "${nameParts[0]} ${nameParts[1].firstOrNull()?.uppercaseChar()}.".uppercase()
                } else if (user?.name?.isNotBlank() == true) {
                    user.name.uppercase()
                } else {
                    ""
                }
                
                android.util.Log.d("MainActivity", "Formatted userName: $userName")
                
                if (cards.isNotEmpty()) {
                    // Выбираем главную карту (первую по displayOrder, или первую в списке)
                    val primaryCard = cards.firstOrNull()
                    
                    // Устанавливаем главную карту
                    if (primaryCard != null) {
                        setMainCard(primaryCard, userName)
                    }
                    
                    // Остальные карты (исключая главную)
                    val otherCards = cards.filter { it.id != primaryCard?.id }
                    
                    // Показываем заголовок "Другие карты" если есть другие карты
                    if (otherCards.isNotEmpty()) {
                        binding.tvOtherCardsTitle.visibility = View.VISIBLE
                        binding.rvOtherCards.visibility = View.VISIBLE
                        compactCardAdapter = CompactCardAdapter(
                            cards = otherCards,
                            onCardClick = { card ->
                                // При клике на карту в списке она становится главной
                                setMainCard(card, userName)
                            }
                        )
                        binding.rvOtherCards.adapter = compactCardAdapter
                    } else {
                        binding.tvOtherCardsTitle.visibility = View.GONE
                        binding.rvOtherCards.visibility = View.GONE
                    }
                } else {
                    // Нет карт - скрываем все
                    binding.cardMainContainer.visibility = View.GONE
                    binding.tvOtherCardsTitle.visibility = View.GONE
                    binding.rvOtherCards.visibility = View.GONE
                }
            }
        } else {
            // Если нет userId, все равно показываем карты
            if (cards.isNotEmpty()) {
                val primaryCard = cards.firstOrNull()
                if (primaryCard != null) {
                    setMainCard(primaryCard, "")
                }
                val otherCards = cards.filter { it.id != primaryCard?.id }
                if (otherCards.isNotEmpty()) {
                    binding.tvOtherCardsTitle.visibility = View.VISIBLE
                    binding.rvOtherCards.visibility = View.VISIBLE
                    compactCardAdapter = CompactCardAdapter(
                        cards = otherCards,
                        onCardClick = { card ->
                            setMainCard(card, "")
                        }
                    )
                    binding.rvOtherCards.adapter = compactCardAdapter
                } else {
                    binding.tvOtherCardsTitle.visibility = View.GONE
                    binding.rvOtherCards.visibility = View.GONE
                }
            } else {
                binding.cardMainContainer.visibility = View.GONE
                binding.tvOtherCardsTitle.visibility = View.GONE
                binding.rvOtherCards.visibility = View.GONE
            }
        }
    }
    
    private fun setMainCard(card: Card, userName: String) {
        mainCard = card
        
        // Удаляем предыдущую главную карту
        binding.cardMainContainer.removeAllViews()
        
        // Создаем новую главную карту
        val cardView = layoutInflater.inflate(R.layout.item_card, binding.cardMainContainer, false)
        val cardViewHolder = CardAdapter.CardViewHolder(cardView)
        cardViewHolder.bind(card, userName, null)
        
        // Обработчик клика на главную карту - открываем детали
        cardView.setOnClickListener {
            val intent = Intent(this, CardDetailsActivity::class.java)
            intent.putExtra("card_id", card.id)
            startActivity(intent)
        }
        
        // Добавляем карту в контейнер
        binding.cardMainContainer.addView(cardView)
        binding.cardMainContainer.visibility = View.VISIBLE
        
        // Обновляем список остальных карт (исключая текущую главную)
        val userId = securityPreferences.getCurrentUserId()
        if (userId != null) {
            lifecycleScope.launch {
                // Получаем имя пользователя для обновления списка
                val user = userRepository.getUserById(userId)
                val nameParts = user?.name?.split(" ") ?: emptyList()
                val currentUserName = if (nameParts.size >= 2 && user?.name?.isNotBlank() == true) {
                    "${nameParts[0]} ${nameParts[1].firstOrNull()?.uppercaseChar()}.".uppercase()
                } else if (user?.name?.isNotBlank() == true) {
                    user.name.uppercase()
                } else {
                    userName
                }
                
                val result = userRepository.getCards(userId)
                result.onSuccess { allCards ->
                    val otherCards = allCards.filter { !it.isHidden && it.id != card.id }
                        .sortedBy { it.displayOrder }
                    
                    if (otherCards.isNotEmpty()) {
                        binding.tvOtherCardsTitle.visibility = View.VISIBLE
                        binding.rvOtherCards.visibility = View.VISIBLE
                        compactCardAdapter = CompactCardAdapter(
                            cards = otherCards,
                            onCardClick = { clickedCard ->
                                setMainCard(clickedCard, currentUserName)
                            }
                        )
                        binding.rvOtherCards.adapter = compactCardAdapter
                    } else {
                        binding.tvOtherCardsTitle.visibility = View.GONE
                        binding.rvOtherCards.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun showEmptyCardState() {
        binding.cardMainContainer.visibility = View.GONE
        binding.tvOtherCardsTitle.visibility = View.GONE
        binding.rvOtherCards.visibility = View.GONE
        binding.cardCreateCardEmpty.visibility = View.VISIBLE
        
        // Настраиваем клик на карточку создания - открываем экран выбора карты
        binding.cardCreateCardEmpty.setOnClickListener {
            openCardSelection()
        }
    }

    private fun hideEmptyCardState() {
        binding.cardCreateCardEmpty.visibility = View.GONE
    }

    private fun setupClickListeners() {
        // Balance toggle
        binding.btnToggleBalance.setOnClickListener {
            isBalanceVisible = !isBalanceVisible
            updateBalanceVisibility()
        }

        // Profile button - выход из профиля
        binding.btnProfile.setOnClickListener {
            showLogoutConfirmation()
        }

        // Notifications button
        binding.btnNotifications.setOnClickListener {
            // TODO: Open notifications
        }

        // Quick actions
        binding.btnTransfer.setOnClickListener {
            val intent = Intent(this, TransferActivity::class.java)
            startActivity(intent)
        }

        binding.btnTopUp.setOnClickListener {
            val intent = Intent(this, DepositActivity::class.java)
            startActivity(intent)
        }

        binding.btnPayment.setOnClickListener {
            val intent = Intent(this, PaymentsActivity::class.java)
            startActivity(intent)
        }

        binding.btnMore.setOnClickListener {
            // TODO: Open more options
        }

        // Bottom navigation
        binding.btnHome.setOnClickListener {
            // Already on home
        }

        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, TransactionsHistoryActivity::class.java)
            startActivity(intent)
        }

        binding.btnQrScanner.setOnClickListener {
            val intent = Intent(this, QRScannerActivity::class.java)
            startActivity(intent)
        }

        binding.btnPayments.setOnClickListener {
            val intent = Intent(this, PaymentsActivity::class.java)
            startActivity(intent)
        }

        binding.btnMenu.setOnClickListener {
            // TODO: Open menu
        }

        binding.tvSeeAll.setOnClickListener {
            val intent = Intent(this, TransactionsHistoryActivity::class.java)
            startActivity(intent)
        }

    }

    private fun updateBalanceVisibility() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                val result = userRepository.getCards(userId)
                result.onSuccess { cards ->
                    // Вычисляем суммарный баланс всех карт
                    val totalBalance = cards.sumOf { it.balance }
                    
                    if (cards.isNotEmpty()) {
                        if (isBalanceVisible) {
                            // Показываем суммарный баланс всех карт
                            binding.tvBalance.text = formatBalance(totalBalance)
                            binding.btnToggleBalance.setImageResource(R.drawable.ic_eye)
                        } else {
                            binding.tvBalance.text = getString(R.string.balance_hidden)
                            binding.btnToggleBalance.setImageResource(R.drawable.ic_eye_off)
                        }
                    } else {
                        // Нет карт - используем заглушку
                        if (isBalanceVisible) {
                            binding.tvBalance.text = formatBalance(0.0)
                            binding.btnToggleBalance.setImageResource(R.drawable.ic_eye)
                        } else {
                            binding.tvBalance.text = getString(R.string.balance_hidden)
                            binding.btnToggleBalance.setImageResource(R.drawable.ic_eye_off)
                        }
                    }
                }.onFailure {
                    // При ошибке используем заглушку
                    if (isBalanceVisible) {
                        binding.tvBalance.text = formatBalance(0.0)
                        binding.btnToggleBalance.setImageResource(R.drawable.ic_eye)
                    } else {
                        binding.tvBalance.text = getString(R.string.balance_hidden)
                        binding.btnToggleBalance.setImageResource(R.drawable.ic_eye_off)
                    }
                }
            }
        }
    }

    private fun formatBalance(amount: Double): String {
        // Format as P (rubles) with 2 decimal places
        return "P ${String.format(Locale.US, "%.2f", amount)}"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Просто закрываем приложение, сохраняя состояние входа
        finishAffinity()
    }
    
    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_confirmation))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun performLogout() {
        // Очищаем все сохраненные данные
        securityPreferences.clearAuthToken()
        securityPreferences.clearPinForEncryption()
        
        // Очищаем PIN и userId локально
        val prefs = getSharedPreferences("security_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .remove("pin_hash")
            .remove("current_user_id")
            .remove("auth_token")
            .apply()
        
        // Переходим на экран приветствия
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun openCardSelection() {
        val intent = Intent(this, CardSelectionActivity::class.java)
        startActivity(intent)
    }
    
    private fun createNewCard() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            val token = securityPreferences.getAuthToken()
            
            if (userId == null || token == null) {
                Toast.makeText(this@MainActivity, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // Проверяем количество карт
            val result = userRepository.getCards(userId)
            result.onSuccess { cards ->
                if (cards.size >= 3) {
                    Toast.makeText(
                        this@MainActivity,
                        "Достигнут лимит карт (максимум 3 карты)",
                        Toast.LENGTH_LONG
                    ).show()
                    return@onSuccess
                }
                
                // Показываем диалог выбора типа карты
                showCardTypeSelectionDialog(userId, token)
            }.onFailure {
                Toast.makeText(this@MainActivity, "Ошибка загрузки карт", Toast.LENGTH_SHORT).show()
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
                .onSuccess {
                    val cardTypeName = if (cardType == "credit") getString(R.string.credit_card) else getString(R.string.debit_card)
                    Toast.makeText(this@MainActivity, "$cardTypeName ${getString(R.string.card_created_successfully)}", Toast.LENGTH_SHORT).show()
                    // Перезагружаем карты
                    loadCards(userId)
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка создания карты: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
    
    private fun toggleCardBlock(card: Card) {
        val action = if (card.isBlocked) getString(R.string.unblock_card) else getString(R.string.block_card)
        val message = if (card.isBlocked) 
            "Вы уверены, что хотите разблокировать карту •••• ${card.number.takeLast(4)}?"
        else 
            "Вы уверены, что хотите заблокировать карту •••• ${card.number.takeLast(4)}?"
        
        AlertDialog.Builder(this)
            .setTitle(action)
            .setMessage(message)
            .setPositiveButton(action) { _, _ ->
                updateCardBlockStatus(card, !card.isBlocked)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateCardBlockStatus(card: Card, isBlocked: Boolean) {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.updateCardBlockStatus(card.id, userId, isBlocked)
                    .onSuccess {
                        Toast.makeText(
                            this@MainActivity,
                            if (isBlocked) getString(R.string.card_blocked) else getString(R.string.card_unblocked),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Перезагружаем карты для обновления UI
                        loadCards(userId)
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@MainActivity,
                            "Ошибка обновления статуса: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }
}

