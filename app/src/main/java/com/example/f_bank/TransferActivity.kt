package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.FavoriteContact
import com.example.f_bank.data.TransferOption
import com.example.f_bank.databinding.ActivityTransferBinding
import com.example.f_bank.ui.FavoriteContactAdapter
import com.example.f_bank.ui.TransferOptionAdapter
import com.example.f_bank.utils.SecurityPreferences
import kotlinx.coroutines.launch

class TransferActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransferBinding
    private lateinit var securityPreferences: SecurityPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)

        setupClickListeners()
        setupFavorites()
        setupTransferOptions()
        setupBottomNavigation()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvViewAll.setOnClickListener {
            val intent = Intent(this, AddFavoriteActivity::class.java)
            startActivity(intent)
        }
        
        // Кнопка добавления избранных
        binding.btnAddFavorite?.setOnClickListener {
            val intent = Intent(this, AddFavoriteActivity::class.java)
            startActivity(intent)
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

        binding.btnTransactions.setOnClickListener {
            val intent = Intent(this, TransactionsHistoryActivity::class.java)
            startActivity(intent)
        }

        binding.btnMenu.setOnClickListener {
            // TODO: Open menu
        }
    }

    private fun setupFavorites() {
        lifecycleScope.launch {
            try {
                val userId = securityPreferences.getCurrentUserId()
                if (userId != null) {
                    val db = AppDatabase.getDatabase(this@TransferActivity)
                    val favoriteContactDao = db.favoriteContactDao()
                    val favoriteEntities = favoriteContactDao.getFavoriteContactsByUserId(userId)
                    val favoriteContacts = favoriteEntities.map { it.toFavoriteContact() }

                    val adapter = FavoriteContactAdapter(favoriteContacts) { contact ->
                        // Открываем экран перевода по телефону с предзаполненным номером
                        val intent = Intent(this@TransferActivity, TransferByPhoneActivity::class.java)
                        contact.phone?.let { phone ->
                            intent.putExtra("phone", phone)
                        }
                        startActivity(intent)
                    }

                    binding.rvFavorites.layoutManager = LinearLayoutManager(this@TransferActivity, LinearLayoutManager.HORIZONTAL, false)
                    binding.rvFavorites.adapter = adapter
                }
            } catch (e: Exception) {
                android.util.Log.e("TransferActivity", "Error loading favorites", e)
            }
        }
    }

    private fun setupTransferOptions() {
        val transferOptions = listOf(
            TransferOption(
                1,
                getString(R.string.between_my_accounts),
                getString(R.string.instant_transfer),
                R.drawable.ic_swap_horizontal
            ),
            TransferOption(
                2,
                getString(R.string.by_phone_number),
                getString(R.string.to_banks_rk_and_within_bank),
                R.drawable.ic_phone
            ),
            TransferOption(
                3,
                getString(R.string.other_bank_card),
                getString(R.string.by_card_number),
                R.drawable.baseline_credit_card_24
            ),
            TransferOption(
                4,
                getString(R.string.international_transfers),
                getString(R.string.swift_western_union),
                R.drawable.ic_globe
            ),
            TransferOption(
                5,
                getString(R.string.by_qr_code),
                getString(R.string.fast_payment),
                R.drawable.baseline_qr_code_24
            )
        )

        val adapter = TransferOptionAdapter(transferOptions) { option ->
            handleTransferOptionClick(option)
        }

        binding.rvTransferOptions.layoutManager = LinearLayoutManager(this)
        binding.rvTransferOptions.adapter = adapter
    }

    private fun handleTransferOptionClick(option: TransferOption) {
        when (option.id) {
            1 -> {
                // Между своими счетами
                val intent = Intent(this, TransferBetweenCardsActivity::class.java)
                startActivity(intent)
            }
            2 -> {
                // По номеру телефона
                val intent = Intent(this, TransferByPhoneActivity::class.java)
                startActivity(intent)
            }
            3 -> {
                // Карта другого банка
                val intent = Intent(this, OtherBankTransferActivity::class.java)
                startActivity(intent)
            }
            4 -> {
                // Международные переводы
                val intent = Intent(this, InternationalTransferActivity::class.java)
                startActivity(intent)
            }
            5 -> {
                // По QR-коду
                val intent = Intent(this, QRScannerActivity::class.java)
                startActivity(intent)
            }
        }
    }
}

