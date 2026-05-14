package com.oc.catemoji.catoc.ui.main.createPony

import android.graphics.drawable.Drawable
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import com.oc.catemoji.catoc.databinding.ItemChooseBinding

class ChoosePonyAdapter(
    private val onClick: (character: CustomModel, position: Int) -> Unit
) : BaseAdapter<CustomModel, ItemChooseBinding>(ItemChooseBinding::inflate) {

    override fun onBind(binding: ItemChooseBinding, item: CustomModel, position: Int) {
        binding.sflShimmer.startShimmer()
        binding.sflShimmer.visibility = View.VISIBLE

        Glide.with(binding.root.context)
            .load(item.avatar)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.sflShimmer.stopShimmer()
                    binding.sflShimmer.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.sflShimmer.stopShimmer()
                    binding.sflShimmer.visibility = View.GONE
                    return false
                }
            })
            .into(binding.imvImage)

        binding.root.setOnClickListener { onClick(item, position) }
    }
}