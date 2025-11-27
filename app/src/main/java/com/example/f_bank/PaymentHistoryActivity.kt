package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.FavoritePayment
import com.example.f_bank.databinding.ActivityPaymentHistoryBinding
import com.example.f_bank.ui.PaymentCategoryAdapter
import com.example.f_bank.utils.SecurityPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PaymentHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentHistoryBinding
    private lateinit var adapter: PaymentCategoryAdapter
    private lateinit var securityPreferences: SecurityPreferences
    private val favoritePayments = mutableListOf<PaymentCategory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)

        setupRecyclerView()
        setupClickListeners()
        setupBottomNavigation()
        loadFavoritePayments()
    }

    private fun setupRecyclerView() {
        adapter = PaymentCategoryAdapter(favoritePayments) { category ->
            handleCategoryClick(category)
        }

        val layoutManager = GridLayoutManager(this, 3)
        binding.rvPaymentHistory.layoutManager = layoutManager
        binding.rvPaymentHistory.adapter = adapter
    }

    private fun loadFavoritePayments() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                try {
                    val payments = withContext(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(this@PaymentHistoryActivity)
                        val favoritePaymentDao = db.favoritePaymentDao()
                        favoritePaymentDao.getFavoritePaymentsByUserId(userId)
                    }

                    favoritePayments.clear()
                    favoritePayments.addAll(payments.map { payment ->
                        PaymentCategory(
                            id = payment.categoryId,
                            title = payment.categoryTitle,
                            description = "",
                            iconRes = payment.iconRes
                        )
                    })

                    adapter = PaymentCategoryAdapter(favoritePayments) { category ->
                        handleCategoryClick(category)
                    }
                    binding.rvPaymentHistory.adapter = adapter

                    if (favoritePayments.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvPaymentHistory.visibility = View.GONE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvPaymentHistory.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PaymentHistoryActivity", "Error loading favorite payments", e)
                    Toast.makeText(this@PaymentHistoryActivity, "Ошибка загрузки истории", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleCategoryClick(category: PaymentCategory) {
        // Обновляем время последнего использования
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@PaymentHistoryActivity)
                    val favoritePaymentDao = db.favoritePaymentDao()
                    favoritePaymentDao.updateLastUsed(userId, category.id, System.currentTimeMillis())
                }
            }
        }

        // Открываем соответствующий экран платежа
        when (category.id) {
            "mobile_utilities" -> {
                openMobilePayment()
            }
            "internet_tv" -> {
                openInternetPayment()
            }
            else -> {
                Toast.makeText(this, "${category.title} - в разработке", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupBottomNavigation() {
        binding.btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
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
    }

    private fun openMobilePayment() {
        val intent = Intent(this, PaymentDetailsActivity::class.java)
        intent.putExtra("category", "mobile")
        startActivity(intent)
    }

    private fun openInternetPayment() {
        val intent = Intent(this, PaymentDetailsActivity::class.java)
        intent.putExtra("category", "internet")
        startActivity(intent)
    }
}

