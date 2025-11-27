package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.f_bank.api.FastForexClient
import com.example.f_bank.databinding.ActivityCurrencyExchangeBinding
import com.example.f_bank.ui.CurrencyRateAdapter
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class CurrencyExchangeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCurrencyExchangeBinding
    private lateinit var adapter: CurrencyRateAdapter
    private val currencyRates = mutableListOf<CurrencyRate>()
    
    // Популярные валюты для отображения
    private val popularCurrencies = listOf(
        "KZT", "RUB", "EUR", "GBP", "JPY", "CNY", "TRY", "AED", "USD"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCurrencyExchangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        loadCurrencyRates()
    }

    private fun setupRecyclerView() {
        adapter = CurrencyRateAdapter(currencyRates)
        binding.rvCurrencyRates.layoutManager = LinearLayoutManager(this)
        binding.rvCurrencyRates.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnRefresh.setOnClickListener {
            loadCurrencyRates()
        }
        
        binding.btnExchange.setOnClickListener {
            val intent = Intent(this, CurrencyExchangeDialogActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadCurrencyRates() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvCurrencyRates.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    FastForexClient.apiService.getAllRates(
                        FastForexClient.getApiKey(),
                        "USD"
                    )
                }

                if (response.isSuccessful && response.body() != null) {
                    val ratesResponse = response.body()!!
                    updateCurrencyRates(ratesResponse)
                } else {
                    showError("Ошибка загрузки курсов валют")
                }
            } catch (e: Exception) {
                android.util.Log.e("CurrencyExchange", "Error loading rates", e)
                showError("Ошибка подключения к серверу")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateCurrencyRates(response: com.example.f_bank.api.model.CurrencyRatesResponse) {
        currencyRates.clear()
        
        // Добавляем популярные валюты
        popularCurrencies.forEach { currency ->
            val rate = response.results[currency]
            if (rate != null) {
                currencyRates.add(CurrencyRate(
                    currency = currency,
                    rate = rate,
                    baseCurrency = response.base
                ))
            }
        }
        
        // Добавляем остальные валюты, отсортированные по коду
        response.results.entries
            .sortedBy { it.key }
            .forEach { (currency, rate) ->
                if (!popularCurrencies.contains(currency)) {
                    currencyRates.add(CurrencyRate(
                        currency = currency,
                        rate = rate,
                        baseCurrency = response.base
                    ))
                }
            }
        
        adapter.notifyDataSetChanged()
        binding.rvCurrencyRates.visibility = View.VISIBLE
        
        // Обновляем время обновления
        binding.tvLastUpdate.text = "Обновлено: ${response.updated}"
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        binding.rvCurrencyRates.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

data class CurrencyRate(
    val currency: String,
    val rate: Double,
    val baseCurrency: String
)

