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
import java.text.NumberFormat
import java.util.Locale

class CompactCardAdapter(
    private val cards: List<Card>,
    private val onCardClick: (Card) -> Unit
) : RecyclerView.Adapter<CompactCardAdapter.CompactCardViewHolder>() {

    class CompactCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.cardView)
        val cardContainer: androidx.constraintlayout.widget.ConstraintLayout = itemView.findViewById(R.id.cardContainer)
        val chipBackground: View = itemView.findViewById(R.id.chipBackground)
        val chipPattern: ImageView = itemView.findViewById(R.id.chipPattern)
        val tvCardNumber: TextView = itemView.findViewById(R.id.tvCardNumber)
        val tvCardBalance: TextView = itemView.findViewById(R.id.tvCardBalance)
        val ivMore: ImageView = itemView.findViewById(R.id.ivMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompactCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card_compact, parent, false)
        return CompactCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompactCardViewHolder, position: Int) {
        val card = cards[position]
        
        // Показываем точки и последние 4 цифры номера карты
        val last4Digits = card.number.takeLast(4)
        holder.tvCardNumber.text = ".... $last4Digits"
        
        // Форматируем баланс
        val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
        formatter.maximumFractionDigits = 0
        holder.tvCardBalance.text = "${formatter.format(card.balance)} ₸"
        
        // Тип карты и визуальное оформление
        if (card.type == "credit") {
            // Кредитная карта: черный фон, серебряный чип, белый текст
            holder.cardContainer.setBackgroundResource(R.drawable.card_ring_white)
            holder.chipBackground.setBackgroundResource(R.drawable.card_chip_silver_gradient)
            holder.chipPattern.setImageResource(R.drawable.card_chip_pattern_silver)
            
            // Белый текст для черной карты
            holder.tvCardNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.card_text_white))
            holder.tvCardBalance.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.card_text_white))
            holder.ivMore.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.card_text_white))
        } else {
            // Дебетовая карта: белый фон, золотой чип, черный текст
            holder.cardContainer.setBackgroundResource(R.drawable.card_border_gray)
            holder.chipBackground.setBackgroundResource(R.drawable.card_chip_gold_gradient)
            holder.chipPattern.setImageResource(R.drawable.card_chip_pattern)
            
            // Черный текст для белой карты
            holder.tvCardNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_primary))
            holder.tvCardBalance.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_primary))
            holder.ivMore.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
        }
        
        // Обработчик клика на карту
        holder.cardView.setOnClickListener {
            onCardClick(card)
        }
        
        // Скрываем иконку "еще" (можно будет добавить позже)
        holder.ivMore.visibility = View.GONE
    }

    override fun getItemCount(): Int = cards.size
}

