package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.oc.catemoji.catoc.databinding.ItemSortBinding

sealed interface DiyAssetItem {
    data class Image(val path: String) : DiyAssetItem
    data class Color(val value: Int) : DiyAssetItem
    data class Key(val value: String) : DiyAssetItem
}

class DiyAssetAdapter(
    private val onItemClick: (DiyAssetItem, Int) -> Unit
) : RecyclerView.Adapter<DiyAssetAdapter.AssetViewHolder>() {

    private var items: List<DiyAssetItem> = emptyList()
    private var selectedPosition = RecyclerView.NO_POSITION

    fun submitList(newItems: List<DiyAssetItem>, selected: Int = RecyclerView.NO_POSITION) {
        items = newItems
        selectedPosition = selected
        notifyDataSetChanged()
    }

    fun select(position: Int) {
        if (position == selectedPosition) return
        val old = selectedPosition
        selectedPosition = position
        if (old in items.indices) notifyItemChanged(old)
        if (position in items.indices) notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        return AssetViewHolder(
            ItemSortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition)
    }

    override fun onViewRecycled(holder: AssetViewHolder) {
        Glide.with(holder.binding.imvImage).clear(holder.binding.imvImage)
        super.onViewRecycled(holder)
    }

    inner class AssetViewHolder(val binding: ItemSortBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DiyAssetItem, selected: Boolean) = with(binding) {
            gradientSort.alpha = if (selected) 1f else 0.82f
            gradientSort.scaleX = if (selected) 1f else 0.94f
            gradientSort.scaleY = if (selected) 1f else 0.94f
            imvImage.background = null
            imvImage.setImageDrawable(null)
            imvImage.isVisible = item !is DiyAssetItem.Key
            tvLabel.isVisible = item is DiyAssetItem.Key

            when (item) {
                is DiyAssetItem.Image -> {
                    Glide.with(imvImage)
                        .load(item.path)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(256, 256)
                        .into(imvImage)
                }

                is DiyAssetItem.Color -> imvImage.setBackgroundColor(item.value)
                is DiyAssetItem.Key -> tvLabel.text = item.value
            }

            root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position], position)
                }
            }
        }
    }
}
