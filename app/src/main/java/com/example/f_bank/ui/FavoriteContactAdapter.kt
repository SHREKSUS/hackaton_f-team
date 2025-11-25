package com.example.f_bank.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.data.FavoriteContact
import com.example.f_bank.databinding.ItemFavoriteContactBinding

class FavoriteContactAdapter(
    private val contacts: List<FavoriteContact>,
    private val onContactClick: (FavoriteContact) -> Unit
) : RecyclerView.Adapter<FavoriteContactAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFavoriteContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.tvName.text = contact.name
        holder.binding.tvInitials.text = contact.initials
        holder.itemView.setOnClickListener {
            onContactClick(contact)
        }
    }

    override fun getItemCount() = contacts.size
}

