package com.example.f_bank

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.f_bank.databinding.ActivityTransferSuccessBinding
import com.example.f_bank.utils.ReceiptGenerator
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

class TransferSuccessActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransferSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем данные из Intent
        val fromCardNumber = intent.getStringExtra("from_card_number") ?: "•••• 1234"
        val toCardNumber = intent.getStringExtra("to_card_number") ?: "•••• 5678"
        val amount = intent.getDoubleExtra("amount", 0.0)
        val description = intent.getStringExtra("description")
        val transactionId = intent.getStringExtra("transaction_id")

        // Отображаем данные
        binding.tvFromCardNumber.text = fromCardNumber
        binding.tvToCardNumber.text = toCardNumber
        
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = 2
        format.minimumFractionDigits = 0
        binding.tvTransferAmount.text = "${format.format(amount)} ₸"

        binding.btnSaveReceipt.setOnClickListener {
            saveAndShareReceipt(fromCardNumber, toCardNumber, amount, description, transactionId)
        }

        binding.btnBackToMain.setOnClickListener {
            // Возвращаемся на главный экран и принудительно обновляем данные
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.putExtra("refresh_cards", true) // Флаг для обновления карт
            intent.putExtra("refresh_transactions", true) // Флаг для обновления транзакций
            intent.putExtra("skip_sync", true) // Пропускаем синхронизацию, чтобы не перезаписать локальные изменения
            startActivity(intent)
            finish()
        }
    }
    
    private fun saveAndShareReceipt(
        fromCard: String,
        toCard: String,
        amount: Double,
        description: String?,
        transactionId: String?
    ) {
        try {
            // Генерируем квитанцию
            val receiptText = ReceiptGenerator.generateReceiptText(
                fromCard, toCard, amount, description, transactionId
            )
            
            // Создаем директорию для квитанций
            val receiptsDir = File(getExternalFilesDir(null), "receipts")
            if (!receiptsDir.exists()) {
                receiptsDir.mkdirs()
            }
            
            // Создаем файл
            val fileName = ReceiptGenerator.generateReceiptFileName(transactionId)
            val receiptFile = File(receiptsDir, fileName)
            
            // Сохраняем квитанцию в файл
            FileOutputStream(receiptFile).use { fos ->
                fos.write(receiptText.toByteArray(Charsets.UTF_8))
            }
            
            // Создаем URI для файла через FileProvider
            val fileUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                receiptFile
            )
            
            // Создаем Intent для отправки файла
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Квитанция о переводе")
                putExtra(Intent.EXTRA_TEXT, receiptText)
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Показываем диалог выбора приложения для отправки
            val chooserIntent = Intent.createChooser(shareIntent, "Сохранить или отправить квитанцию")
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooserIntent)
            
            Toast.makeText(this, "Квитанция сохранена", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("TransferSuccess", "Ошибка при сохранении квитанции", e)
            Toast.makeText(this, "Ошибка при сохранении квитанции: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

