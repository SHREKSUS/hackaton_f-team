package com.example.f_bank

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.R
import com.example.f_bank.data.Card
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityManageCardsBinding
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ManageCardsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityManageCardsBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var adapter: ManageCardsAdapter
    private var cards: MutableList<Card> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageCardsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)

        setupRecyclerView()
        setupClickListeners()
        loadCards()
    }

    private fun setupRecyclerView() {
        adapter = ManageCardsAdapter(
            cards = cards,
            onVisibilityChanged = { card, isHidden ->
                updateCardVisibility(card, isHidden)
            },
            onBlockClick = { card ->
                toggleCardBlock(card)
            },
            onDeleteClick = { card ->
                showDeleteConfirmation(card)
            }
        )
        binding.rvCards.layoutManager = LinearLayoutManager(this)
        binding.rvCards.adapter = adapter

        // Настраиваем drag-and-drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    return false
                }

                // Обновляем порядок в списке
                val movedCard = cards.removeAt(fromPosition)
                cards.add(toPosition, movedCard)
                adapter.notifyItemMoved(fromPosition, toPosition)

                // Сохраняем новый порядок
                saveCardOrder()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Не используется
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvCards)
        adapter.setItemTouchHelper(itemTouchHelper)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnCreateCard.setOnClickListener {
            createNewCard()
        }
    }

    private fun createNewCard() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            val token = securityPreferences.getAuthToken()
            
            if (userId == null || token == null) {
                Toast.makeText(this@ManageCardsActivity, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // Проверяем количество карт
            val result = userRepository.getCards(userId)
            result.onSuccess { existingCards ->
                if (existingCards.size >= 3) {
                    Toast.makeText(
                        this@ManageCardsActivity,
                        "Достигнут лимит карт (максимум 3 карты)",
                        Toast.LENGTH_LONG
                    ).show()
                    return@onSuccess
                }
                
                // Показываем диалог выбора типа карты
                showCardTypeSelectionDialog(userId, token)
            }.onFailure {
                Toast.makeText(this@ManageCardsActivity, "Ошибка проверки карт", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@ManageCardsActivity, "$cardTypeName ${getString(R.string.card_created_successfully)}", Toast.LENGTH_SHORT).show()
                    // Перезагружаем карты
                    loadCards()
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@ManageCardsActivity,
                        "Ошибка создания карты: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun loadCards() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.getCards(userId)
                    .onSuccess { loadedCards ->
                        // Сортируем по порядку отображения
                        cards = loadedCards.sortedBy { it.displayOrder }.toMutableList()
                        adapter.updateCards(cards)
                        updateEmptyState()
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@ManageCardsActivity,
                            "Ошибка загрузки карт: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        updateEmptyState()
                    }
            }
        }
    }

    private fun updateEmptyState() {
        if (cards.isEmpty()) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvCards.visibility = View.GONE
            binding.tvInfo.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvCards.visibility = View.VISIBLE
            binding.tvInfo.visibility = View.VISIBLE
        }
    }

    private fun saveCardOrder() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                // Обновляем порядок для всех карт
                cards.forEachIndexed { index, card ->
                    userRepository.updateCardOrder(card.id, userId, index)
                        .onFailure {
                            Toast.makeText(
                                this@ManageCardsActivity,
                                "Ошибка сохранения порядка",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
        }
    }

    private fun updateCardVisibility(card: Card, isHidden: Boolean) {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.updateCardVisibility(card.id, userId, isHidden)
                    .onSuccess {
                        // Обновляем карту в списке
                        val index = cards.indexOfFirst { it.id == card.id }
                        if (index >= 0) {
                            cards[index] = card.copy(isHidden = isHidden)
                            adapter.updateCards(cards)
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@ManageCardsActivity,
                            "Ошибка обновления видимости: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Откатываем изменение в UI
                        loadCards()
                    }
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
                        val index = cards.indexOfFirst { it.id == card.id }
                        if (index >= 0) {
                            cards[index] = card.copy(isBlocked = isBlocked)
                            adapter.updateCards(cards)
                            Toast.makeText(
                                this@ManageCardsActivity,
                                if (isBlocked) getString(R.string.card_blocked) else getString(R.string.card_unblocked),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@ManageCardsActivity,
                            "Ошибка обновления статуса: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }

    private fun showDeleteConfirmation(card: Card) {
        AlertDialog.Builder(this)
            .setTitle("Удаление карты")
            .setMessage("Вы уверены, что хотите удалить карту •••• ${card.number.takeLast(4)}?")
            .setPositiveButton("Удалить") { _, _ ->
                deleteCard(card)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteCard(card: Card) {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.deleteCard(card.id, userId)
                    .onSuccess {
                        // Удаляем карту из списка
                        val index = cards.indexOfFirst { it.id == card.id }
                        if (index >= 0) {
                            cards.removeAt(index)
                            adapter.updateCards(cards)
                            // Обновляем порядок оставшихся карт
                            saveCardOrder()
                            // Обновляем состояние пустого списка
                            updateEmptyState()
                            Toast.makeText(
                                this@ManageCardsActivity,
                                "Карта удалена",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@ManageCardsActivity,
                            "Ошибка удаления карты: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Перезагружаем карты при возврате на экран
        loadCards()
    }
}

class ManageCardsAdapter(
    private var cards: List<Card>,
    private val onVisibilityChanged: (Card, Boolean) -> Unit,
    private val onBlockClick: (Card) -> Unit,
    private val onDeleteClick: (Card) -> Unit
) : RecyclerView.Adapter<ManageCardsAdapter.ViewHolder>() {

    private var itemTouchHelper: ItemTouchHelper? = null

    class ViewHolder(val binding: com.example.f_bank.databinding.ItemManageCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val binding = com.example.f_bank.databinding.ItemManageCardBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0

        // Маскируем номер карты
        val lastFourDigits = card.number.takeLast(4)
        val cardTypeLabel = if (card.type == "credit") "CREDIT" else "DEBIT"
        holder.binding.tvCardNumber.text = "$cardTypeLabel •••• $lastFourDigits"

        // Баланс
        holder.binding.tvCardBalance.text = "${format.format(card.balance)} ₸"

        // Переключатель скрытия (инвертирован: если карта скрыта, переключатель выключен)
        // Удаляем предыдущий listener, чтобы избежать срабатывания при инициализации
        holder.binding.switchHide.setOnCheckedChangeListener(null)
        holder.binding.switchHide.isChecked = !card.isHidden
        holder.binding.switchHide.setOnCheckedChangeListener { _, isChecked ->
            onVisibilityChanged(card, !isChecked)
        }

        // Кнопка блокировки
        holder.binding.btnBlock.setOnClickListener {
            onBlockClick(card)
        }
        
        // Обновляем иконку блокировки в зависимости от статуса
        val context = holder.itemView.context
        if (card.isBlocked) {
            holder.binding.btnBlock.setImageResource(R.drawable.ic_delete)
            holder.binding.btnBlock.contentDescription = context.getString(R.string.unblock_card)
        } else {
            holder.binding.btnBlock.setImageResource(R.drawable.ic_delete)
            holder.binding.btnBlock.contentDescription = context.getString(R.string.block_card)
        }

        // Кнопка удаления
        holder.binding.btnDelete.setOnClickListener {
            onDeleteClick(card)
        }

        // Настраиваем drag handle для перетаскивания
        holder.binding.ivDragHandle.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = cards.size

    fun updateCards(newCards: List<Card>) {
        cards = newCards
        notifyDataSetChanged()
    }

    fun setItemTouchHelper(itemTouchHelper: ItemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper
    }
}

