package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.databinding.ItemSortBinding

class DiyDrawAdapter(
    private val onItemClick: (DrawableDraw) -> Unit
) : RecyclerView.Adapter<DiyDrawAdapter.DrawViewHolder>() {

    private var items: List<DrawableDraw> = emptyList()

    fun submitList(newItems: List<DrawableDraw>) {
        items = newItems.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawViewHolder {
        return DrawViewHolder(
            ItemSortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DrawViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class DrawViewHolder(
        private val binding: ItemSortBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(draw: DrawableDraw) = with(binding) {
            gradientSort.alpha = 1f
            gradientSort.scaleX = 1f
            gradientSort.scaleY = 1f
            imvImage.background = null
            imvImage.setImageDrawable(draw.drawable)
            imvImage.isVisible = true
            tvLabel.isVisible = false
            root.setOnClickListener { onItemClick(draw) }
        }
    }
}
