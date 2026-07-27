package com.oc.catemoji.catoc.ui.main.add_character.adapter

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.databinding.ItemSpeechBinding
import com.oc.catemoji.catoc.databinding.ItemStickerBinding
import com.oc.catemoji.catoc.utils.DataLocal

class SpeechAdapter  : BaseAdapter<SelectedAddModel, ItemSpeechBinding>(ItemSpeechBinding::inflate) {
    var onItemClick: ((String, Drawable?) -> Unit) = { _, _ -> }
    var currentSelected = -1

    override fun onBind(binding: ItemSpeechBinding, item: SelectedAddModel, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

        binding.apply {
            Glide.with(binding.root)
                .load(item.path)
                .override(256, 256)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(shimmerDrawable)
                .into(imvImage)
//            loadImage(root, item.path, imageView)
            root.onClick {
                selectItem(position)          // ← was missing entirely
                val preview = imvImage.drawable?.constantState?.newDrawable()?.mutate()
                onItemClick.invoke(item.path, preview)
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