package com.oc.catemoji.catoc.ui.main.cusomdiypet

import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.ItemEgeBinding
import com.oc.catemoji.catoc.databinding.ItemStickerBinding
import com.oc.catemoji.catoc.databinding.ItemTittlePetBinding
import com.oc.catemoji.catoc.utils.DataLocal

class DiyCatStickerAdapter(
    private val onItemClick: (String) -> Unit
) : BaseAdapter<String, ItemEgeBinding>(ItemEgeBinding::inflate) {

    override fun onBind(binding: ItemEgeBinding, item: String, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }
        Glide.with(binding.imvImage)
            .load(item)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(shimmerDrawable)
            .override(400, 400)
            .into(binding.imvImage)
        binding.root.onClick { onItemClick(item) }
    }
}

class DiyCatTitleAdapter(
    private val onItemClick: (Int) -> Unit
) : BaseAdapter<String, ItemTittlePetBinding>(ItemTittlePetBinding::inflate) {

    private var selectedPosition = 0

    fun setSelectedPosition(position: Int) {
        if (position == selectedPosition) return
        val oldPosition = selectedPosition
        selectedPosition = position
        if (oldPosition in items.indices) notifyItemChanged(oldPosition)
        if (position in items.indices) notifyItemChanged(position)
    }

    override fun onBind(binding: ItemTittlePetBinding, item: String, position: Int) {
        binding.txtTittle.text = item
        if (position == selectedPosition) {
            binding.txtTittle.setTextColor(ContextCompat.getColor(binding.root.context, R.color.app_color))
            binding.txtTittle.setBackgroundResource(R.drawable.bg_select_title_pet)
        } else {  binding.txtTittle.setTextColor(ContextCompat.getColor(binding.root.context, R.color.origin2))
            binding.txtTittle.setBackgroundResource(R.drawable.bg_unselect_title_pet)
        }

        binding.root.onClick {
            setSelectedPosition(position)
            onItemClick(position)
        }
    }
}
