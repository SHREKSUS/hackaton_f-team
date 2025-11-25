package com.example.f_bank

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.R
import com.example.f_bank.data.Card
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityCardDetailsBinding
import com.example.f_bank.utils.CardEncryption
import com.example.f_bank.utils.PhoneNumberFormatter
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.utils.onFailure
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class CardDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCardDetailsBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private var cardId: Long = -1
    private var currentCard: Card? = null
    private var isCardNumberVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)

        cardId = intent.getLongExtra("card_id", -1)
        if (cardId == -1L) {
            Toast.makeText(this, "Ошибка: карта не найдена", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        loadCardDetails()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnToggleCardNumber.setOnClickListener {
            toggleCardNumberVisibility()
        }
        
        binding.btnCopyCardNumber.setOnClickListener {
            copyCardNumberToClipboard()
        }
        
        // Также можно копировать при клике на сам номер карты
        binding.tvCardNumberFull.setOnClickListener {
            copyCardNumberToClipboard()
        }
        
        binding.btnBlockCard.setOnClickListener {
            currentCard?.let { card ->
                toggleCardBlock(card)
            }
        }
        
        binding.btnLinkPhone.setOnClickListener {
            currentCard?.let { card ->
                if (card.linkedPhone != null) {
                    showUnlinkPhoneDialog(card)
                } else {
                    showLinkPhoneDialog(card)
                }
            }
        }
    }
    
    private fun copyCardNumberToClipboard() {
        currentCard?.let { card ->
            // Копируем номер карты без пробелов
            val cardNumber = card.number.replace(" ", "").replace("-", "")
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.card_number_label), cardNumber)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.card_number_copied), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun toggleCardNumberVisibility() {
        isCardNumberVisible = !isCardNumberVisible
        currentCard?.let { card ->
            updateCardNumberDisplay(card)
        }
    }
    
    private fun updateCardNumberDisplay(card: Card) {
        val last4Digits = card.number.takeLast(4)
        if (isCardNumberVisible) {
            // Показываем полный номер карты
            val formattedNumber = formatCardNumber(card.number)
            binding.tvCardNumber.text = formattedNumber
            binding.tvCardNumberFull.text = formattedNumber
            binding.btnToggleCardNumber.setImageResource(R.drawable.ic_eye_off)
        } else {
            // Показываем замаскированный номер в формате как на главной странице
            binding.tvCardNumber.text = ".... .... .... $last4Digits"
            binding.tvCardNumberFull.text = ".... .... .... $last4Digits"
            binding.btnToggleCardNumber.setImageResource(R.drawable.ic_eye)
        }
    }
    
    private fun formatCardNumber(cardNumber: String): String {
        val cleanNumber = cardNumber.replace(" ", "").replace("-", "")
        return if (cleanNumber.length == 16) {
            "${cleanNumber.substring(0, 4)} ${cleanNumber.substring(4, 8)} ${cleanNumber.substring(8, 12)} ${cleanNumber.substring(12, 16)}"
        } else {
            cleanNumber
        }
    }

    private fun loadCardDetails() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.getCards(userId)
                    .onSuccess { cards ->
                        val card = cards.find { it.id == cardId }
                        if (card != null) {
                            displayCardDetails(card)
                        } else {
                            Toast.makeText(this@CardDetailsActivity, "Карта не найдена", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(this@CardDetailsActivity, "Ошибка загрузки карты: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun displayCardDetails(card: Card) {
        currentCard = card
        
        // По умолчанию показываем замаскированный номер карты
        isCardNumberVisible = false
        updateCardNumberDisplay(card)

        // Тип карты для информации ниже
        val cardTypeText = when (card.type.lowercase()) {
            "debit" -> "Дебетовая"
            "credit" -> "Кредитная"
            else -> card.type
        }
        binding.tvCardType.text = cardTypeText

        // Баланс для информации ниже
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        binding.tvBalance.text = "${format.format(card.balance)} ₸"
        
        // Баланс на карте
        binding.tvCardBalance.text = "${format.format(card.balance)} ₸"

        // Валюта
        binding.tvCurrency.text = card.currency

        // Срок действия
        binding.tvExpiry.text = card.expiry ?: "12/28"
        binding.tvExpiryDate.text = card.expiry ?: "12/28"

        // Имя держателя карты (из имени пользователя)
        val userId = securityPreferences.getCurrentUserId()
        if (userId != null) {
            lifecycleScope.launch {
                val user = userRepository.getUserById(userId)
                user?.let {
                    // Форматируем ФИО для карты: показываем "ФАМИЛИЯ И." в верхнем регистре
                    val nameParts = it.name.split(" ")
                    val displayName = if (nameParts.size >= 2) {
                        "${nameParts[0]} ${nameParts[1].firstOrNull()?.uppercaseChar()}.".uppercase()
                    } else {
                        it.name.uppercase()
                    }
                    binding.tvCardholderName.text = displayName
                }
            }
        }
        
        // Тип карты и визуальное оформление на карте
        if (card.type == "credit") {
            // Кредитная карта: желтый акцент
            binding.tvCardTypeBadge.text = "CREDIT"
            binding.tvCardTypeBadge.visibility = View.VISIBLE
            binding.cvCardPreview.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_credit_bg))
            binding.viewChip.setBackgroundResource(R.drawable.ic_card_chip_yellow)
            binding.viewAccentLine.setBackgroundResource(R.drawable.card_accent_line_yellow)
            binding.tvExpiry.setTextColor(ContextCompat.getColor(this, R.color.card_text_white))
        } else {
            // Дебетовая карта: синий акцент
            binding.tvCardTypeBadge.text = "DEBIT"
            binding.tvCardTypeBadge.visibility = View.VISIBLE
            binding.cvCardPreview.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_dark_bg))
            binding.viewChip.setBackgroundResource(R.drawable.ic_card_chip_blue)
            binding.viewAccentLine.setBackgroundResource(R.drawable.card_accent_line_blue)
            binding.tvExpiry.setTextColor(ContextCompat.getColor(this, R.color.card_text_white))
        }
        
        // Визуальная индикация заблокированной карты
        if (card.isBlocked) {
            binding.cvCardPreview.alpha = 0.6f
            binding.tvBlockedBadge.visibility = View.VISIBLE
        } else {
            binding.cvCardPreview.alpha = 1.0f
            binding.tvBlockedBadge.visibility = View.GONE
        }
        
        // Обновляем кнопку блокировки
        updateBlockButton(card)
        
        // Обновляем информацию о привязанном номере телефона
        updateLinkedPhoneDisplay(card)
    }
    
    private fun updateLinkedPhoneDisplay(card: Card) {
        if (card.linkedPhone != null) {
            binding.tvLinkedPhone.text = PhoneNumberFormatter.formatDigits(card.linkedPhone)
            binding.btnLinkPhone.text = "Отвязать"
        } else {
            binding.tvLinkedPhone.text = "Не привязан"
            binding.btnLinkPhone.text = "Привязать"
        }
    }
    
    private fun showLinkPhoneDialog(card: Card) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_link_phone, null)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)
        
        // Настраиваем форматирование номера телефона
        val phoneFormatter = PhoneNumberFormatter(etPhone)
        etPhone.addTextChangedListener(phoneFormatter)
        
        AlertDialog.Builder(this)
            .setTitle("Привязать номер телефона")
            .setMessage("Введите номер телефона для привязки к карте. На этот номер можно будет переводить деньги.")
            .setView(dialogView)
            .setPositiveButton("Привязать") { _, _ ->
                val phone = etPhone.text.toString().trim()
                if (phone.isNotEmpty()) {
                    val cleanedPhone = PhoneNumberFormatter.unformatPhone(phone)
                    if (cleanedPhone.length == 11 && cleanedPhone.startsWith("7")) {
                        linkPhoneToCard(card, cleanedPhone)
                    } else {
                        Toast.makeText(this, "Введите корректный номер телефона (7XXXXXXXXXX)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showUnlinkPhoneDialog(card: Card) {
        AlertDialog.Builder(this)
            .setTitle("Отвязать номер телефона")
            .setMessage("Вы уверены, что хотите отвязать номер телефона ${PhoneNumberFormatter.formatDigits(card.linkedPhone!!)} от этой карты?")
            .setPositiveButton("Отвязать") { _, _ ->
                linkPhoneToCard(card, null)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun linkPhoneToCard(card: Card, phone: String?) {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.linkPhoneToCard(card.id, userId, phone)
                    .onSuccess {
                        Toast.makeText(
                            this@CardDetailsActivity,
                            if (phone != null) "Номер телефона успешно привязан" else "Номер телефона успешно отвязан",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Перезагружаем карту для обновления UI
                        loadCardDetails()
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@CardDetailsActivity,
                            "Ошибка: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }
    
    private fun updateBlockButton(card: Card) {
        if (card.isBlocked) {
            binding.btnBlockCard.text = getString(R.string.unblock_card)
            binding.btnBlockCard.setBackgroundColor(ContextCompat.getColor(this, R.color.transaction_positive))
            binding.cvCardPreview.alpha = 0.6f
        } else {
            binding.btnBlockCard.text = getString(R.string.block_card)
            binding.btnBlockCard.setBackgroundColor(ContextCompat.getColor(this, R.color.pin_error))
            binding.cvCardPreview.alpha = 1.0f
        }
    }
    
    private fun toggleCardBlock(card: Card) {
        val action = if (card.isBlocked) getString(R.string.unblock_card) else getString(R.string.block_card)
        val message = if (card.isBlocked) 
            "Вы уверены, что хотите разблокировать карту •••• ${card.number.takeLast(4)}?"
        else 
            "Вы уверены, что хотите заблокировать карту •••• ${card.number.takeLast(4)}?"
        
        AlertDialog.Builder(this)
            .setTitle(action)
            .setMessage(message)
            .setPositiveButton(action) { _, _ ->
                updateCardBlockStatus(card, !card.isBlocked)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateCardBlockStatus(card: Card, isBlocked: Boolean) {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.updateCardBlockStatus(card.id, userId, isBlocked)
                    .onSuccess {
                        Toast.makeText(
                            this@CardDetailsActivity,
                            if (isBlocked) getString(R.string.card_blocked) else getString(R.string.card_unblocked),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Перезагружаем карту для обновления UI
                        loadCardDetails()
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@CardDetailsActivity,
                            "Ошибка обновления статуса: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }
}

