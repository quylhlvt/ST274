package com.oc.catemoji.catoc.ui.main.add_character.adapter

import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.databinding.ItemBackgroundImageBinding
import com.oc.catemoji.catoc.utils.DataLocal.shimmer


class BackgroundImageAdapter : BaseAdapter<SelectedAddModel, ItemBackgroundImageBinding>(
    ItemBackgroundImageBinding::inflate
) {
    var onAddImageClick: (() -> Unit) = {}
    var onNoneImageClick: ((Int) -> Unit) = {}
    var onBackgroundImageClick: ((String, Int) -> Unit) = { _, _ -> }
    var currentSelected = -1

    override fun onBind(binding: ItemBackgroundImageBinding, item: SelectedAddModel, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(shimmer) }

        binding.apply {
            tvAddImage.isSelected = position == 0

            if (currentSelected == position) {
                materialForcus.visible()
            } else {
                materialForcus.gone()
            }

            if (position == 0) {
                lnlAddItem.visible()
                imvImage.gone()
                imvNoneItem.gone()
                lnlAddItem.onClick { onAddImageClick() }
            } else if (position == 1) {
                lnlAddItem.gone()
                imvImage.gone()
                imvNoneItem.visible()
                imvNoneItem.onClick { onNoneImageClick(position) }
            } else {
                lnlAddItem.gone()
                imvNoneItem.gone()
                imvImage.visible()
                if (imvImage.tag != item.path) {
                    imvImage.tag = item.path

                    Glide.with(binding.root)
                        .load(item.path.toUri())
                        .override(120, 120)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .placeholder(shimmerDrawable)
                        .into(binding.imvImage)
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
