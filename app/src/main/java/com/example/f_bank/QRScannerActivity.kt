package com.example.f_bank

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.data.Card
import com.example.f_bank.data.UserRepository
import com.example.f_bank.databinding.ActivityQrScannerBinding
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onFailure
import com.example.f_bank.utils.onSuccess
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.launch
import org.json.JSONObject

class QRScannerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var userRepository: UserRepository
    private var isShowingQR = false
    private var cards: List<Card> = emptyList()
    private var selectedCard: Card? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (!isShowingQR) {
                startScanning()
            }
        } else {
            Toast.makeText(
                this,
                "Разрешение на камеру необходимо для сканирования QR-кода",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userRepository = UserRepository(this)

        setupClickListeners()
        loadCards()
        showScanningMode()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnToggleMode.setOnClickListener {
            if (isShowingQR) {
                showScanningMode()
            } else {
                showQRCodeMode()
            }
        }
    }

    private fun loadCards() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            if (userId != null) {
                userRepository.getCards(userId)
                    .onSuccess { cardList ->
                        cards = cardList.filter { !it.isHidden && !it.isBlocked }
                        if (cards.isNotEmpty()) {
                            selectedCard = cards[0]
                            if (isShowingQR) {
                                generateQRCode()
                            }
                        }
                    }
                    .onFailure { error ->
                        Toast.makeText(
                            this@QRScannerActivity,
                            "Ошибка загрузки карт: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }

    private fun showScanningMode() {
        isShowingQR = false
        binding.scannerView.visibility = android.view.View.VISIBLE
        binding.qrCodeContainer.visibility = android.view.View.GONE
        binding.btnToggleMode.text = "Показать мой QR-код"

        checkCameraPermission()
    }

    private fun showQRCodeMode() {
        isShowingQR = true
        binding.scannerView.visibility = android.view.View.GONE
        binding.qrCodeContainer.visibility = android.view.View.VISIBLE
        binding.btnToggleMode.text = "Сканировать QR-код"

        stopScanning()
        generateQRCode()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startScanning()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startScanning() {
        binding.scannerView.resume()
        binding.scannerView.decodeContinuous(callback)
    }

    private fun stopScanning() {
        binding.scannerView.pause()
    }

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            if (result == null || result.text == null) return

            val qrData = result.text
            handleQRCodeScanned(qrData)
        }

        override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
    }

    private fun handleQRCodeScanned(qrData: String) {
        stopScanning()
        
        try {
            // Парсим JSON из QR-кода
            val json = JSONObject(qrData)
            val userId = json.optLong("userId", -1)
            val cardNumber = json.optString("cardNumber", "")
            val userName = json.optString("name", "")

            if (userId > 0 && cardNumber.isNotEmpty()) {
                // Открываем экран перевода
                val intent = Intent(this, TransferActivity::class.java)
                intent.putExtra("qr_scanned", true)
                intent.putExtra("recipient_user_id", userId)
                intent.putExtra("recipient_card_number", cardNumber)
                intent.putExtra("recipient_name", userName)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Неверный формат QR-кода", Toast.LENGTH_SHORT).show()
                startScanning()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка чтения QR-кода: ${e.message}", Toast.LENGTH_SHORT).show()
            startScanning()
        }
    }

    private fun generateQRCode() {
        val userId = securityPreferences.getCurrentUserId() ?: return
        val card = selectedCard ?: return

        lifecycleScope.launch {
            // Получаем информацию о пользователе
            val user = userRepository.getUserById(userId)
            if (user != null) {
                // Создаем JSON с данными для перевода
                val qrData = JSONObject().apply {
                    put("userId", userId)
                    put("cardNumber", card.number.takeLast(4)) // Последние 4 цифры
                    put("name", user.name)
                    put("phone", user.phone)
                }

                // Генерируем QR-код
                val qrBitmap = generateQRCodeBitmap(qrData.toString(), 512)
                binding.ivQRCode.setImageBitmap(qrBitmap)
                
                // Показываем информацию о карте
                binding.tvCardInfo.text = "Карта •••• ${card.number.takeLast(4)}"
            }
        }
    }

    private fun generateQRCodeBitmap(text: String, size: Int): Bitmap {
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }

        return bitmap
    }

    override fun onResume() {
        super.onResume()
        if (!isShowingQR) {
            binding.scannerView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.scannerView.pause()
    }
}
