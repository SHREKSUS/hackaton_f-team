package com.example.f_bank.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.CurrencyRate
import com.example.f_bank.R
import java.text.NumberFormat
import java.util.Locale

class CurrencyRateAdapter(
    private val rates: List<CurrencyRate>
) : RecyclerView.Adapter<CurrencyRateAdapter.CurrencyRateViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurrencyRateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_currency_rate, parent, false)
        return CurrencyRateViewHolder(view)
    }

    override fun onBindViewHolder(holder: CurrencyRateViewHolder, position: Int) {
        holder.bind(rates[position])
    }

    override fun getItemCount(): Int = rates.size

    class CurrencyRateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCurrency: TextView = itemView.findViewById(R.id.tvCurrency)
        private val tvRate: TextView = itemView.findViewById(R.id.tvRate)
        private val tvCurrencyName: TextView = itemView.findViewById(R.id.tvCurrencyName)

        fun bind(rate: CurrencyRate) {
            tvCurrency.text = rate.currency
            
            val numberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
            numberFormat.maximumFractionDigits = 4
            numberFormat.minimumFractionDigits = 4
            tvRate.text = numberFormat.format(rate.rate)
            
            // Получаем название валюты
            val currencyName = getCurrencyName(rate.currency)
            tvCurrencyName.text = currencyName
        }
        
        private fun getCurrencyName(code: String): String {
            return when (code) {
                "USD" -> "Доллар США"
                "EUR" -> "Евро"
                "RUB" -> "Российский рубль"
                "KZT" -> "Казахстанский тенге"
                "GBP" -> "Фунт стерлингов"
                "JPY" -> "Японская иена"
                "CNY" -> "Китайский юань"
                "TRY" -> "Турецкая лира"
                "AED" -> "Дирхам ОАЭ"
                else -> code
            }
        }
    }
}

