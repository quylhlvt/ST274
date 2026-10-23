package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.custom.DrawableDraw
import com.oc.catemoji.catoc.databinding.ItemSortBinding

class DiyDrawAdapter(
    private val onItemClick: (DrawableDraw) -> Unit
) : RecyclerView.Adapter<DiyDrawAdapter.DrawViewHolder>() {

    private var items = mutableListOf<DrawableDraw>()
    private var thumbnails = mutableListOf<Drawable>()
    private var selectedDraw: DrawableDraw? = null

    fun submitList(newItems: List<DrawableDraw>, newSelectedDraw: DrawableDraw?) {
        val newThumbnails = newItems.map { it.drawable }
        val validSelectedDraw = newSelectedDraw?.takeIf { selected ->
            newItems.any { it === selected }
        }
        if (items == newItems &&
            thumbnails == newThumbnails &&
            selectedDraw === validSelectedDraw
        ) {
            return
        }
        val previousItems = items.toList()
        val previousThumbnails = thumbnails.toList()
        val previousSelectedDraw = selectedDraw
        val changes = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previousItems.size
            override fun getNewListSize() = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previousItems[oldItemPosition] === newItems[newItemPosition]

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previousThumbnails[oldItemPosition] === newThumbnails[newItemPosition] &&
                        (previousItems[oldItemPosition] === previousSelectedDraw) ==
                        (newItems[newItemPosition] === validSelectedDraw)
        })
        items = newItems.toMutableList()
        thumbnails = newThumbnails.toMutableList()
        selectedDraw = validSelectedDraw
        changes.dispatchUpdatesTo(this)
    }

    fun selectDraw(draw: DrawableDraw?) {
        val validDraw = draw?.takeIf { selected -> items.any { it === selected } }
        if (selectedDraw === validDraw) return

        val previousPosition = items.indexOfFirst { it === selectedDraw }
        selectedDraw = validDraw
        val selectedPosition = items.indexOfFirst { it === selectedDraw }
        if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        items.add(to, items.removeAt(from))
        thumbnails.add(to, thumbnails.removeAt(from))
        notifyItemMoved(from, to)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawViewHolder {
        return DrawViewHolder(
            ItemSortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DrawViewHolder, position: Int) {
        val draw = items[position]
        holder.bind(draw, draw === selectedDraw)
    }

    inner class DrawViewHolder(
        private val binding: ItemSortBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(draw: DrawableDraw, selected: Boolean) = with(binding) {
            val strokeColor = ContextCompat.getColor(
                root.context,
                if (selected) R.color.app_color else R.color.transparent
            )
            gradientSort.setGradientColors(strokeColor, strokeColor, strokeColor)
            gradientSort.alpha = 1f
            gradientSort.scaleX = 1f
            gradientSort.scaleY = 1f
            imvImage.setImageDrawable(draw.drawable)
            imvImage.isVisible = true
            tvLabel.isVisible = false
            root.isSelected = selected
            root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onItemClick(items[position])
            }
        }
    }
}
