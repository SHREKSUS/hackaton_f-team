package com.example.f_bank

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.f_bank.data.AppDatabase
import com.example.f_bank.data.FavoriteContactEntity
import com.example.f_bank.databinding.ActivityAddFavoriteBinding
import com.example.f_bank.ui.ContactAdapter
import com.example.f_bank.utils.SecurityPreferences
import kotlinx.coroutines.launch

class AddFavoriteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddFavoriteBinding
    private lateinit var securityPreferences: SecurityPreferences
    private lateinit var favoriteContactDao: com.example.f_bank.data.FavoriteContactDao
    private var contacts: List<ContactItem> = emptyList()
    private var userId: Long? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        securityPreferences = SecurityPreferences(this)
        userId = securityPreferences.getCurrentUserId()
        
        val db = AppDatabase.getDatabase(this)
        favoriteContactDao = db.favoriteContactDao()

        setupClickListeners()
        checkContactsPermission()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnFromContacts.setOnClickListener {
            if (checkContactsPermission()) {
                loadContacts()
            }
        }

        binding.btnFromHistory.setOnClickListener {
            val intent = Intent(this, AddFavoriteFromHistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkContactsPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContacts()
            } else {
                Toast.makeText(this, "Разрешение на чтение контактов необходимо", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            try {
                val contactsList = mutableListOf<ContactItem>()
                val cursor: Cursor? = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone._ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                cursor?.use {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (it.moveToNext()) {
                        val name = it.getString(nameIndex)
                        val phone = it.getString(phoneIndex)?.replace(Regex("[^0-9]"), "")
                        
                        if (phone != null && phone.length >= 10) {
                            // Нормализуем номер телефона
                            val normalizedPhone = if (phone.startsWith("8")) {
                                "7${phone.substring(1)}"
                            } else if (!phone.startsWith("7") && phone.length == 10) {
                                "7$phone"
                            } else {
                                phone
                            }
                            
                            if (normalizedPhone.length == 11 && normalizedPhone.startsWith("7")) {
                                contactsList.add(ContactItem(name, normalizedPhone))
                            }
                        }
                    }
                }

                contacts = contactsList.distinctBy { it.phone }
                displayContacts()
            } catch (e: Exception) {
                Toast.makeText(this@AddFavoriteActivity, "Ошибка загрузки контактов: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayContacts() {
        val adapter = ContactAdapter(contacts) { contact ->
            addToFavorites(contact.name, contact.phone)
        }
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = adapter
    }

    private fun addToFavorites(name: String, phone: String) {
        lifecycleScope.launch {
            try {
                userId?.let { uid ->
                    // Проверяем, не добавлен ли уже этот контакт
                    val existing = favoriteContactDao.getFavoriteContactByPhone(uid, phone)
                    if (existing != null) {
                        Toast.makeText(this@AddFavoriteActivity, "Контакт уже в избранном", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val favoriteContact = FavoriteContactEntity(
                        userId = uid,
                        name = name,
                        phone = phone
                    )
                    favoriteContactDao.insertFavoriteContact(favoriteContact)
                    Toast.makeText(this@AddFavoriteActivity, "Контакт добавлен в избранное", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddFavoriteActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class ContactItem(val name: String, val phone: String)
}

