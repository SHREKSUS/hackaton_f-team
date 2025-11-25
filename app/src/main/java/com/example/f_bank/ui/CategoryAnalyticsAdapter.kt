package com.example.f_bank.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.R
import com.example.f_bank.data.TransactionCategory
import java.text.NumberFormat
import java.util.Locale

data class CategoryAnalyticsItem(
    val category: TransactionCategory,
    val amount: Double,
    val percentage: Double,
    val iconRes: Int = R.drawable.ic_wallet,
    val color: Int = 0xFF000000.toInt()
)

class CategoryAnalyticsAdapter(
    private var items: List<CategoryAnalyticsItem> = emptyList()
) : RecyclerView.Adapter<CategoryAnalyticsAdapter.ViewHolder>() {
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconContainer: View = itemView.findViewById(R.id.iconContainer)
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvPercentage: TextView = itemView.findViewById(R.id.tvPercentage)
        val progressBar: View = itemView.findViewById(R.id.progressBar)
        val progressBarContainer: View = itemView.findViewById(R.id.progressBarContainer)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_analytics, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val numberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
        numberFormat.maximumFractionDigits = 0
        
        holder.tvCategoryName.text = item.category.displayName
        holder.tvAmount.text = "${numberFormat.format(item.amount)} Р"
        holder.tvPercentage.text = "${item.percentage.toInt()}%"
        holder.ivIcon.setImageResource(item.iconRes)
        
        // Устанавливаем цвет иконки контейнера
        holder.iconContainer.setBackgroundColor(item.color)
        
        // Устанавливаем цвет иконки (белый для темных цветов, черный для светлых)
        val iconColor = if (item.color == 0xFFCCCCCC.toInt()) {
            0xFF000000.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
        holder.ivIcon.setColorFilter(iconColor)
        
        // Устанавливаем ширину прогресс-бара
        val layoutParams = holder.progressBar.layoutParams
        val containerWidth = holder.progressBarContainer.width
        if (containerWidth > 0) {
            layoutParams.width = (containerWidth * item.percentage / 100).toInt()
        } else {
            // Если ширина еще не измерена, используем процент от родителя
            holder.progressBarContainer.post {
                val width = holder.progressBarContainer.width
                holder.progressBar.layoutParams.width = (width * item.percentage / 100).toInt()
                holder.progressBar.requestLayout()
            }
        }
        holder.progressBar.layoutParams = layoutParams
        
        // Устанавливаем цвет прогресс-бара
        holder.progressBar.setBackgroundColor(item.color)
    }
    
    override fun getItemCount(): Int = items.size
    
    fun updateItems(newItems: List<CategoryAnalyticsItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
