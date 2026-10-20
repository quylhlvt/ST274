package com.oc.catemoji.catoc.ui.main.myPony.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.databinding.ItemMyCreationTabBinding
import com.oc.catemoji.catoc.ui.main.myPony.MyCreationTab

class MyCreationTabAdapter(
    private val onTabSelected: (MyCreationTab) -> Unit
) : RecyclerView.Adapter<MyCreationTabAdapter.TabViewHolder>() {

    private var selectedPosition = 0
    private val tabs = MyCreationTab.values().toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemMyCreationTabBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TabViewHolder(binding)
    }

    override fun getItemCount() = tabs.size

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(tabs[position].titleRes, position == selectedPosition)
        holder.itemView.setOnClickListener {
            val clickedPosition = holder.bindingAdapterPosition
            if (clickedPosition != RecyclerView.NO_POSITION) {
                onTabSelected(tabs[clickedPosition])
            }
        }
    }

    fun selectTab(tab: MyCreationTab) {
        val position = tabs.indexOf(tab)
        if (selectedPosition == position) return
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }

    class TabViewHolder(private val binding: ItemMyCreationTabBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: Int, selected: Boolean) {
            binding.root.isSelected = selected
            binding.tvTabTitle.setText(title)
            binding.tvTabTitle.isSelected = true
            binding.imvTabBackground.setBackgroundResource(
                if (selected) R.drawable.bg_btn_type_selected
                else R.drawable.bg_btn_type_unselected
            )
        }
    }
}
