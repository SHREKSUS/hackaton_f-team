package com.example.f_bank

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.data.Card
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityPaymentDetailsBinding
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class PaymentDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentDetailsBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private var cards: List<Card> = emptyList()
    private var selectedCard: Card? = null
    private var paymentCategory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)
        paymentCategory = intent.getStringExtra("category") ?: ""

        setupClickListeners()
        loadCards()
        setupCategory()
    }

    private fun setupCategory() {
        val title = when (paymentCategory) {
            "utilities" -> getString(R.string.utilities)
            "mobile" -> getString(R.string.mobile_services)
            "internet" -> getString(R.string.internet_services)
            else -> getString(R.string.payments)
        }
        binding.tvTitle.text = title

        // Настраиваем поля в зависимости от категории
        when (paymentCategory) {
            "utilities" -> {
                binding.tilAccountNumber.hint = "Номер лицевого счета"
                binding.tilAccountNumber.visibility = android.view.View.VISIBLE
            }
            "mobile" -> {
                binding.tilAccountNumber.hint = "Номер телефона"
                binding.tilAccountNumber.visibility = android.view.View.VISIBLE
            }
            "internet" -> {
                binding.tilAccountNumber.hint = "Номер договора"
                binding.tilAccountNumber.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.cvCard.setOnClickListener {
            showCardSelectionDialog()
        }

        binding.btnPay.setOnClickListener {
            performPayment()
        }
    }

    private fun loadCards() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.getCards(userId)
                    .onSuccess { cardList ->
                        cards = cardList.filter { !it.isHidden }
                        if (cards.isNotEmpty()) {
                            selectedCard = cards[0]
                            updateCardDisplay()
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(this@PaymentDetailsActivity, "Ошибка загрузки карт: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun showCardSelectionDialog() {
        if (cards.isEmpty()) {
            Toast.makeText(this, "У вас нет карт", Toast.LENGTH_SHORT).show()
            return
        }

        val cardTitles = cards.map { card ->
            val lastFour = card.number.takeLast(4)
            "•••• $lastFour (${formatBalance(card.balance)} ₸)"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите карту")
            .setItems(cardTitles.toTypedArray()) { _, which ->
                selectedCard = cards[which]
                updateCardDisplay()
            }
            .show()
    }

    private fun updateCardDisplay() {
        selectedCard?.let { card ->
            val lastFour = card.number.takeLast(4)
            binding.tvCardNumber.text = "•••• $lastFour"
            binding.tvCardBalance.text = "Баланс: ${formatBalance(card.balance)} ₸"
        }
    }

    private fun performPayment() {
        val accountNumber = binding.etAccountNumber.text.toString().trim()
        val amountText = binding.etAmount.text.toString().trim()

        if (selectedCard == null) {
            Toast.makeText(this, "Выберите карту", Toast.LENGTH_SHORT).show()
            return
        }

        if (accountNumber.isEmpty()) {
            binding.tilAccountNumber.error = "Заполните поле"
            return
        }

        if (amountText.isEmpty()) {
            binding.tilAmount.error = "Введите сумму"
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.tilAmount.error = "Введите корректную сумму"
            return
        }

        if (amount > (selectedCard?.balance ?: 0.0)) {
            binding.tilAmount.error = "Недостаточно средств"
            return
        }

        // Здесь должна быть логика оплаты через API
        Toast.makeText(this, getString(R.string.payment_successful), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun formatBalance(balance: Double): String {
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        return format.format(balance)
    }
}

