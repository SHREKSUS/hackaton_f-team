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
import java.util.Locale

class TransactionAdapter(
    private var transactions: List<Transaction>
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.ivTransactionIcon)
        private val titleView: TextView = itemView.findViewById(R.id.tvTransactionTitle)
        private val timeView: TextView = itemView.findViewById(R.id.tvTransactionTime)
        private val amountView: TextView = itemView.findViewById(R.id.tvTransactionAmount)
        private val iconBackground: View = itemView.findViewById(R.id.iconContainer)

        fun bind(transaction: Transaction) {
            titleView.text = transaction.title
            timeView.text = transaction.time

            // Set icon from transaction
            iconView.setImageResource(transaction.iconRes)

            // Set icon background based on type
            when (transaction.type) {
                TransactionType.TRANSFER_OUT -> {
                    iconBackground.setBackgroundResource(R.drawable.icon_transfer_bg)
                }
                TransactionType.TRANSFER_IN -> {
                    iconBackground.setBackgroundResource(R.drawable.icon_transfer_bg)
                }
                TransactionType.DEPOSIT -> {
                    iconBackground.setBackgroundResource(R.drawable.icon_deposit_bg)
                }
                TransactionType.PURCHASE -> {
                    iconBackground.setBackgroundResource(R.drawable.icon_shopping_bg)
                }
            }

            // Format amount with sign and color (use P for rubles)
            val absAmount = kotlin.math.abs(transaction.amount)
            val formattedAmount = "P ${String.format(Locale.US, "%.2f", absAmount)}"
            if (transaction.amount >= 0) {
                amountView.text = "+$formattedAmount"
                amountView.setTextColor(itemView.context.getColor(R.color.transaction_positive))
            } else {
                amountView.text = "-$formattedAmount"
                amountView.setTextColor(itemView.context.getColor(R.color.transaction_negative))
            }
        }
    }
}

