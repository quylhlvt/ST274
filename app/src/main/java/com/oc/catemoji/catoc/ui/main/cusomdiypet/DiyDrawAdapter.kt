package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.drawable.Drawable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.databinding.ItemSortBinding

class DiyDrawAdapter(
    private val onItemClick: (DrawableDraw) -> Unit
) : RecyclerView.Adapter<DiyDrawAdapter.DrawViewHolder>() {

    private var items: List<DrawableDraw> = emptyList()
    private var thumbnails: List<Drawable> = emptyList()

    fun submitList(newItems: List<DrawableDraw>) {
        val newThumbnails = newItems.map { it.drawable }
        if (items == newItems && thumbnails == newThumbnails) return
        val previousItems = items
        val previousThumbnails = thumbnails
        val changes = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previousItems.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previousItems[oldItemPosition] === newItems[newItemPosition]
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previousThumbnails[oldItemPosition] === newThumbnails[newItemPosition]
        })
        items = newItems.toList()
        thumbnails = newThumbnails
        changes.dispatchUpdatesTo(this)
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
            imvImage.setImageDrawable(draw.drawable)
            imvImage.isVisible = true
            tvLabel.isVisible = false
            root.setOnClickListener { onItemClick(draw) }
        }
    }
}
