package com.example.f_bank.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.R
import com.example.f_bank.data.Transaction
import com.example.f_bank.data.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GroupedTransactionAdapter(
    private var transactions: List<Transaction>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_TRANSACTION = 1
    }

    private val currencyFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
    private val dateFormat = SimpleDateFormat("d MMM", Locale.forLanguageTag("ru"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru"))

    data class GroupedItem(
        val type: Int,
        val date: String? = null,
        val transaction: Transaction? = null
    )

    private var groupedItems: List<GroupedItem> = emptyList()

    init {
        updateGroupedItems()
    }

    private fun updateGroupedItems() {
        val grouped = mutableListOf<GroupedItem>()
        var currentDate: String? = null

        transactions.forEach { transaction ->
            val transactionDate = getDateString(transaction.timestamp)
            
            if (transactionDate != currentDate) {
                currentDate = transactionDate
                grouped.add(GroupedItem(TYPE_DATE_HEADER, date = currentDate))
            }
            
            grouped.add(GroupedItem(TYPE_TRANSACTION, transaction = transaction))
        }

        groupedItems = grouped
    }

    private fun getDateString(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        return dateFormat.format(calendar.time)
    }

    override fun getItemViewType(position: Int): Int {
        return groupedItems[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            TYPE_TRANSACTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction, parent, false)
                TransactionViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = groupedItems[position]
        when (item.type) {
            TYPE_DATE_HEADER -> {
                (holder as DateHeaderViewHolder).bind(item.date ?: "")
            }
            TYPE_TRANSACTION -> {
                (holder as TransactionViewHolder).bind(item.transaction!!)
            }
        }
    }

    override fun getItemCount(): Int = groupedItems.size

    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions = newTransactions
        updateGroupedItems()
        notifyDataSetChanged()
    }

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateView: TextView = itemView as TextView

        fun bind(date: String) {
            dateView.text = date
        }
    }

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.ivTransactionIcon)
        private val titleView: TextView = itemView.findViewById(R.id.tvTransactionTitle)
        private val timeView: TextView = itemView.findViewById(R.id.tvTransactionTime)
        private val categoryView: TextView = itemView.findViewById(R.id.tvTransactionCategory)
        private val amountView: TextView = itemView.findViewById(R.id.tvTransactionAmount)
        private val iconContainer: View = itemView.findViewById(R.id.iconContainer)

        fun bind(transaction: Transaction) {
            titleView.text = transaction.title
            
            // Форматируем время
            val calendar = Calendar.getInstance().apply {
                timeInMillis = transaction.timestamp
            }
            timeView.text = timeFormat.format(calendar.time)
            
            // Определяем категорию
            val category = when (transaction.type) {
                TransactionType.TRANSFER_OUT -> "Переводы друзьям"
                TransactionType.TRANSFER_IN -> "Входящие"
                TransactionType.DEPOSIT -> "Пополнение"
                TransactionType.PURCHASE -> "Оплата услуг"
            }
            categoryView.text = category

            // Устанавливаем иконку и фон
            when (transaction.type) {
                TransactionType.TRANSFER_OUT -> {
                    iconView.setImageResource(R.drawable.ic_transfer_up)
                    iconContainer.setBackgroundResource(R.drawable.icon_circle_bg)
                    iconView.setColorFilter(itemView.context.getColor(R.color.text_primary))
                }
                TransactionType.TRANSFER_IN, TransactionType.DEPOSIT -> {
                    iconView.setImageResource(R.drawable.ic_transfer_down)
                    iconContainer.setBackgroundResource(R.drawable.icon_circle_dark_bg)
                    iconView.setColorFilter(itemView.context.getColor(R.color.white))
                }
                TransactionType.PURCHASE -> {
                    iconView.setImageResource(R.drawable.ic_transfer_up)
                    iconContainer.setBackgroundResource(R.drawable.icon_circle_bg)
                    iconView.setColorFilter(itemView.context.getColor(R.color.text_primary))
                }
            }

            // Форматируем сумму
            val absAmount = kotlin.math.abs(transaction.amount)
            val formattedAmount = currencyFormat.format(absAmount)
            
            if (transaction.amount >= 0) {
                amountView.text = "+ $formattedAmount Р"
                amountView.setTextColor(itemView.context.getColor(R.color.text_primary))
            } else {
                amountView.text = "- $formattedAmount Р"
                amountView.setTextColor(itemView.context.getColor(R.color.text_primary))
            }
        }
    }
}

