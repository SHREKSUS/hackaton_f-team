package com.example.f_bank

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.f_bank.R
import com.example.f_bank.databinding.ActivityWelcomeBinding
import com.example.f_bank.data.UserRepository
import com.example.f_bank.utils.ParticleSplashEffect
import com.example.f_bank.utils.SecurityPreferences
import com.example.f_bank.utils.onSuccess
import com.example.f_bank.utils.onFailure
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var securityPreferences: SecurityPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        securityPreferences = SecurityPreferences(this)

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Проверяем, есть ли сохраненный вход
        checkAutoLogin()

        binding.btnLogin.setOnClickListener {
            markFirstLaunchCompleted()
            ParticleSplashEffect.play(
                it,
                ContextCompat.getColor(this, R.color.button_primary_bg)
            )
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        binding.btnCreateAccount.setOnClickListener {
            markFirstLaunchCompleted()
            ParticleSplashEffect.play(
                it,
                ContextCompat.getColor(this, R.color.button_secondary_bg)
            )
            val intent = Intent(this, RegisterStep1Activity::class.java)
            startActivity(intent)
        }
    }

    private fun checkAutoLogin() {
        lifecycleScope.launch {
            val userId = securityPreferences.getCurrentUserId()
            val token = securityPreferences.getAuthToken()
            
            // Если есть сохраненный userId и токен, автоматически входим
            if (userId != null && token != null) {
                val userRepository = UserRepository(this@WelcomeActivity)
                
                // Синхронизируем карты с сервером для обновления балансов
                userRepository.syncCardsFromServer(userId, token)
                
                // Проверяем, установлен ли PIN-код
                if (securityPreferences.isPinSet()) {
                    // Если PIN установлен, переходим на экран ввода PIN
                    val intent = Intent(this@WelcomeActivity, CreatePinActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    // Если PIN не установлен, проверяем наличие карты
                    val hasCards = userRepository.checkCards(token)
                    hasCards.onSuccess { hasCardsResult ->
                        if (hasCardsResult) {
                            // Есть карты, переходим на главный экран
                            val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            // Нет карт, показываем экран выбора карты
                            val intent = Intent(this@WelcomeActivity, CardSelectionActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }.onFailure {
                        // При ошибке переходим на главный экран
                        val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    private fun isFirstLaunch(): Boolean {
        return sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    private fun markFirstLaunchCompleted() {
        sharedPreferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    companion object {
        private const val PREFS_NAME = "FBankPrefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }
}
