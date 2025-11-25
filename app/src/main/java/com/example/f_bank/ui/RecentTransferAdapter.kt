package com.example.f_bank.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.databinding.ItemRecentTransferBinding

class RecentTransferAdapter(
    private val phones: List<String>,
    private val onPhoneClick: (String) -> Unit
) : RecyclerView.Adapter<RecentTransferAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecentTransferBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentTransferBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val phone = phones[position]
        holder.binding.tvPhone.text = formatPhone(phone)
        
        // Генерируем инициалы из номера (последние 2 цифры)
        val initials = phone.takeLast(2)
        holder.binding.tvInitials.text = initials
        
        holder.itemView.setOnClickListener {
            onPhoneClick(phone)
        }
    }

    override fun getItemCount() = phones.size

    private fun formatPhone(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return if (digits.length == 11 && digits.startsWith("7")) {
            "+7 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 9)}-${digits.substring(9)}"
        } else {
            phone
        }
    }
}

