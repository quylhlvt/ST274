package com.oc.catemoji.catoc.ui.main.cusomdiypet

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.databinding.ItemBackgroundTextBinding

class DiyTextBackgroundAdapter(
    private val onItemClick: (String?) -> Boolean
) : BaseAdapter<String?, ItemBackgroundTextBinding>(ItemBackgroundTextBinding::inflate) {

    private var selectedPosition = 0

    override fun onBind(binding: ItemBackgroundTextBinding, item: String?, position: Int) {
        Glide.with(binding.imgBackGroundText).clear(binding.imgBackGroundText)
        binding.imgBackGroundText.setImageDrawable(null)

        val isNoneItem = item == null
        binding.stokerBackground.isVisible = !isNoneItem
        binding.stokerBackgroundNone.isVisible = isNoneItem
        updateSelectionColor(binding, position)

        if (item != null) {
            Glide.with(binding.imgBackGroundText)
                .load(item)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .into(binding.imgBackGroundText)
        }

        binding.root.setOnClickListener {
            if (!onItemClick(item)) return@setOnClickListener
            val oldPosition = selectedPosition
            if (oldPosition != position) {
                selectedPosition = position
                if (oldPosition in items.indices) {
                    notifyItemChanged(oldPosition, PAYLOAD_SELECTION)
                }
                notifyItemChanged(position, PAYLOAD_SELECTION)
            }
        }
    }

    override fun onBindPayload(
        binding: ItemBackgroundTextBinding,
        item: String?,
        position: Int,
        payloads: List<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            updateSelectionColor(binding, position)
        } else {
            super.onBindPayload(binding, item, position, payloads)
        }
    }

    private fun updateSelectionColor(binding: ItemBackgroundTextBinding, position: Int) {
        val strokeColor = ContextCompat.getColor(
            binding.root.context,
            if (position == selectedPosition) R.color.app_color else R.color.transparent
        )
        binding.stokerBackground.setGradientColors(strokeColor, strokeColor, strokeColor)
        binding.stokerBackgroundNone.setGradientColors(strokeColor, strokeColor, strokeColor)
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}
