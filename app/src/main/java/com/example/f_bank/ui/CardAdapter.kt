package com.example.f_bank.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.R
import com.example.f_bank.data.Card
import com.example.f_bank.utils.CardEncryption
import java.text.NumberFormat
import java.util.Locale

class CardAdapter(
    private val cards: List<Card>,
    private val showCreateButton: Boolean,
    private val cardholderName: String = "",
    private val onCardClick: (Card) -> Unit,
    private val onCreateCardClick: () -> Unit,
    private val onBlockClick: ((Card) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CARD = 0
        private const val TYPE_CREATE_BUTTON = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < cards.size) TYPE_CARD else TYPE_CREATE_BUTTON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CARD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_card, parent, false)
                CardViewHolder(view)
            }
            TYPE_CREATE_BUTTON -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_create_card_button, parent, false)
                CreateButtonViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                val card = cards[position]
                holder.bind(card, cardholderName, onBlockClick)
                holder.itemView.setOnClickListener {
                    onCardClick(card)
                }
            }
            is CreateButtonViewHolder -> {
                holder.itemView.setOnClickListener {
                    onCreateCardClick()
                }
            }
        }
    }

    override fun getItemCount(): Int = cards.size + if (showCreateButton) 1 else 0

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCardNumber: TextView = itemView.findViewById(R.id.tvCardNumber)
        private val tvCardBalance: TextView = itemView.findViewById(R.id.tvCardBalance)
        private val tvCardholderName: TextView = itemView.findViewById(R.id.tvCardholderName)
        private val tvExpiry: TextView = itemView.findViewById(R.id.tvExpiry)
        private val tvCardType: TextView = itemView.findViewById(R.id.tvCardType)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.cardView)
        private val cardContainer: View = itemView.findViewById(R.id.cardContainer)
        private val viewChip: ViewGroup = itemView.findViewById(R.id.viewChip)
        private val chipBackground: View = itemView.findViewById(R.id.chipBackground)
        private val chipPattern: ImageView = itemView.findViewById(R.id.chipPattern)
        private val ivWifi: ImageView = itemView.findViewById(R.id.ivWifi)
        private val tvBlockedBadge: TextView = itemView.findViewById(R.id.tvBlockedBadge)

        fun bind(card: Card, cardholderName: String = "", onBlockClick: ((Card) -> Unit)? = null) {
            // Показываем 4 точки и последние 4 цифры номера карты
            val last4Digits = card.number.takeLast(4)
            tvCardNumber.text = ".... $last4Digits"

            // Отображаем баланс карты справа
            val format = NumberFormat.getNumberInstance(Locale.getDefault())
            format.maximumFractionDigits = 2
            format.minimumFractionDigits = 0
            tvCardBalance.text = "${format.format(card.balance)} ₸"

            // Имя держателя карты
            tvCardholderName.text = cardholderName.uppercase().ifEmpty { "CARDHOLDER" }

            // Срок действия
            tvExpiry.text = card.expiry ?: "12/28"

            // Тип карты и визуальное оформление
            if (card.type == "credit") {
                // Кредитная карта: черный фон, серебряный чип, белый текст, светлое кольцо
                tvCardType.visibility = View.GONE
                cardContainer.setBackgroundResource(R.drawable.card_ring_white)
                chipBackground.setBackgroundResource(R.drawable.card_chip_silver_gradient)
                chipPattern.setImageResource(R.drawable.card_chip_pattern_silver)
                
                // Белый текст для черной карты
                tvCardNumber.setTextColor(ContextCompat.getColor(itemView.context, R.color.card_text_white))
                tvCardBalance.setTextColor(ContextCompat.getColor(itemView.context, R.color.card_text_white))
                tvCardholderName.setTextColor(ContextCompat.getColor(itemView.context, R.color.card_text_white))
                tvExpiry.setTextColor(ContextCompat.getColor(itemView.context, R.color.card_text_white))
                tvCardType.setTextColor(ContextCompat.getColor(itemView.context, R.color.card_text_white))
                ivWifi.setColorFilter(ContextCompat.getColor(itemView.context, R.color.card_text_white))
            } else {
                // Дебетовая карта: белый фон, золотой чип, черный текст, серая рамка
                tvCardType.visibility = View.GONE
                cardContainer.setBackgroundResource(R.drawable.card_border_gray)
                chipBackground.setBackgroundResource(R.drawable.card_chip_gold_gradient)
                chipPattern.setImageResource(R.drawable.card_chip_pattern)
                
                // Черный текст для белой карты
                tvCardNumber.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvCardBalance.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvCardholderName.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvExpiry.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvCardType.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                ivWifi.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_primary))
            }
            
            // Визуальная индикация заблокированной карты
            if (card.isBlocked) {
                cardView.alpha = 0.6f
                tvBlockedBadge.visibility = View.VISIBLE
            } else {
                cardView.alpha = 1.0f
                tvBlockedBadge.visibility = View.GONE
            }
        }
    }

    class CreateButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}

