package com.oc.catemoji.catoc.ui.main.createPony

import android.graphics.drawable.Drawable
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.databinding.ItemChooseBinding
import com.oc.catemoji.catoc.utils.DataLocal

class ChoosePonyAdapter(
    private val onClick: (character: CustomModel, position: Int) -> Unit
) : BaseAdapter<CustomModel, ItemChooseBinding>(ItemChooseBinding::inflate) {

    override fun onBind(binding: ItemChooseBinding, item: CustomModel, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }


        if (binding.imvImage.tag != item.avatar) {
            binding.imvImage.tag = item.avatar
            Glide.with(binding.imvImage)
                .load(item.avatar)
                .placeholder(shimmerDrawable)
                .into(binding.imvImage)
        }

        binding.root.setOnClickListener { onClick(item, position) }
    }
}
