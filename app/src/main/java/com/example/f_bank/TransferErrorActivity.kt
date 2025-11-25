package com.example.f_bank

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.f_bank.databinding.ActivityTransferErrorBinding

class TransferErrorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransferErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем сообщение об ошибке из Intent
        val errorMessage = intent.getStringExtra("error_message") ?: "Не удалось выполнить перевод"
        binding.tvErrorMessage.text = errorMessage

        binding.btnTryAgain.setOnClickListener {
            finish()
        }

        binding.btnBackToMain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}

