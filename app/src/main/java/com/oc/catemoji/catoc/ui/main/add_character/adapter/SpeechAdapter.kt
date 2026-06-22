package com.oc.catemoji.catoc.ui.main.add_character.adapter

import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.databinding.ItemStickerBinding
import com.oc.catemoji.catoc.utils.DataLocal

class SpeechAdapter  : BaseAdapter<SelectedAddModel, ItemStickerBinding>(ItemStickerBinding::inflate) {
    var onItemClick: ((String) -> Unit) = {}
    var currentSelected = -1

    override fun onBind(binding: ItemStickerBinding, item: SelectedAddModel, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

        binding.apply {
            if (currentSelected == position) {
                shadown.visible()
                materialParent.apply {
                    strokeColor = ContextCompat.getColor(context, R.color.app_color)
                    setCardBackgroundColor(ContextCompat.getColor(context, R.color.app_color4))
                }
            } else {
                shadown.gone()
                materialParent.apply {
                    strokeColor = ContextCompat.getColor(context, R.color.app_color7)
                    setCardBackgroundColor(ContextCompat.getColor(context, R.color.app_color8))
                }
            }
            Glide.with(binding.root)
                .load(item.path)
                .override(256, 256)
                .placeholder(shimmerDrawable)
                .into(imageView)
//            loadImage(root, item.path, imageView)
            root.onClick {
                selectItem(position)          // ← was missing entirely
                onItemClick.invoke(item.path)
            }
        }
    }

    fun selectItem(position: Int) {           // ← changed private → public
        if (position == currentSelected) return
        val old = currentSelected
        currentSelected = position
        if (old >= 0) notifyItemChanged(old)
        notifyItemChanged(position)
    }
}