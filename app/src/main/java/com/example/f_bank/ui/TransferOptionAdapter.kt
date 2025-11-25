package com.example.f_bank.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.f_bank.R
import com.example.f_bank.data.TransferOption
import com.example.f_bank.databinding.ItemTransferOptionBinding

class TransferOptionAdapter(
    private val options: List<TransferOption>,
    private val onOptionClick: (TransferOption) -> Unit
) : RecyclerView.Adapter<TransferOptionAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTransferOptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransferOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option = options[position]
        holder.binding.tvTitle.text = option.title
        holder.binding.tvSubtitle.text = option.subtitle
        holder.binding.ivIcon.setImageResource(option.iconRes)
        holder.itemView.setOnClickListener {
            onOptionClick(option)
        }
    }

    override fun getItemCount() = options.size
}

