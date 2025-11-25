package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.f_bank.databinding.ActivityPaymentsBinding
import com.example.f_bank.ui.PaymentCategoryAdapter
import kotlinx.coroutines.launch

class PaymentsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        val categories = listOf(
            PaymentCategory("utilities", getString(R.string.utilities), "Оплата коммунальных услуг", R.drawable.ic_transfer_up),
            PaymentCategory("mobile", getString(R.string.mobile_services), "Пополнение мобильного телефона", R.drawable.ic_transfer_up),
            PaymentCategory("internet", getString(R.string.internet_services), "Оплата интернет-сервисов", R.drawable.ic_transfer_up),
            PaymentCategory("other_bank", getString(R.string.other_bank_transfer), "Перевод на карту другого банка", R.drawable.ic_transfer_up)
        )

        val adapter = PaymentCategoryAdapter(categories) { category ->
            when (category.id) {
                "utilities" -> openUtilitiesPayment()
                "mobile" -> openMobilePayment()
                "internet" -> openInternetPayment()
                "other_bank" -> openOtherBankTransfer()
                else -> Toast.makeText(this, "Функция в разработке", Toast.LENGTH_SHORT).show()
            }
        }

        binding.rvPaymentCategories.layoutManager = LinearLayoutManager(this)
        binding.rvPaymentCategories.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun openUtilitiesPayment() {
        val intent = Intent(this, PaymentDetailsActivity::class.java)
        intent.putExtra("category", "utilities")
        startActivity(intent)
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

    private fun openOtherBankTransfer() {
        val intent = Intent(this, OtherBankTransferActivity::class.java)
        startActivity(intent)
    }
}

data class PaymentCategory(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int
)

