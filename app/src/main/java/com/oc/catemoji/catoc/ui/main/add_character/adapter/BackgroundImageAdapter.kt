package com.oc.catemoji.catoc.ui.main.add_character.adapter

import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.loadFromAsset
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.select
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.databinding.ItemBackgroundImageBinding
import com.oc.catemoji.catoc.utils.DataLocal
import com.oc.catemoji.catoc.utils.DataLocal.shimmer


class BackgroundImageAdapter : BaseAdapter<SelectedAddModel, ItemBackgroundImageBinding>(
    ItemBackgroundImageBinding::inflate
) {
    var onAddImageClick: (() -> Unit) = {}
    var onBackgroundImageClick: ((String, Int) -> Unit) = { _, _ -> }
    var currentSelected = -1

    override fun onBind(binding: ItemBackgroundImageBinding, item: SelectedAddModel, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(shimmer) }

        val context = binding.root.context
        binding.apply {
            if (currentSelected == position) {
                shadown.visible()
                materialParent.apply {    strokeColor = ContextCompat.getColor(context, R.color.app_color)
                    setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.app_color4)
                    )}
            } else {
                shadown.gone()
                materialParent.apply { strokeColor = ContextCompat.getColor(context, R.color.app_color7)
                    setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.app_color8)
                    )
                    // tắt elevation mặc định để dùng custom shadow
                }
            }
            if (position == 0) {
                imvAddItem.visible()
                imvImage.gone()
                imvAddItem.onClick { onAddImageClick() }
            } else {
                imvAddItem.gone()
                imvImage.visible()
                if (imvImage.tag != item.path) {
                    imvImage.tag = item.path

                    Glide.with(binding.root)
                        .load(Uri.parse(item.path))
                        .override(120, 120)                           // ← QUAN TRỌNG NHẤT
                        .diskCacheStrategy(DiskCacheStrategy.ALL)     // ← cache, scroll lại không decode
                        .centerCrop()
                        .placeholder(shimmerDrawable)
                        .into(binding.imvImage)
//                    imvImage.loadFromAsset(item.path)
                }
                imvImage.onClick { onBackgroundImageClick(item.path, position) }
            }
        }
    }

    fun selectItem(position: Int) {
        if (position == currentSelected) return
        val old = currentSelected
        currentSelected = position
        if (old >= 0) notifyItemChanged(old)
        if (position >= 0) notifyItemChanged(position)
    }

    fun clearSelection() {
        if (currentSelected < 0) return
        val old = currentSelected
        currentSelected = -1
        notifyItemChanged(old)
    }
}