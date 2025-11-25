package com.example.f_bank.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.AddFavoriteActivity
import com.example.f_bank.databinding.ItemContactBinding

class ContactAdapter(
    private val contacts: List<AddFavoriteActivity.ContactItem>,
    private val onContactClick: (AddFavoriteActivity.ContactItem) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.tvName.text = contact.name
        holder.binding.tvPhone.text = formatPhone(contact.phone)
        
        // Генерируем инициалы
        val initials = generateInitials(contact.name)
        holder.binding.tvInitials.text = initials
        
        holder.itemView.setOnClickListener {
            onContactClick(contact)
        }
    }

    override fun getItemCount() = contacts.size

    private fun formatPhone(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return if (digits.length == 11 && digits.startsWith("7")) {
            "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits.substring(9)}"
        } else {
            phone
        }
    }

    private fun generateInitials(name: String): String {
        val parts = name.trim().split(" ")
        return when {
            parts.isEmpty() -> "??"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }
}

