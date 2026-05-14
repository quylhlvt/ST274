package com.oc.catemoji.catoc.ui.main.add_character.adapter

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.loadFromAsset
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.databinding.ItemBackgroundColorBinding


class BackgroundColorAdapter : BaseAdapter<SelectedAddModel, ItemBackgroundColorBinding>(
    ItemBackgroundColorBinding::inflate
) {
    var onChooseColorClick: (() -> Unit) = {}
    var onBackgroundColorClick: ((Int, Int) -> Unit) = { _, _ -> }
    var currentSelected = -1

    override fun onBind(binding: ItemBackgroundColorBinding, item: SelectedAddModel, position: Int) {
        val context = binding.root.context

        binding.apply {
            // ← chỉ dùng currentSelected, không dùng item.isSelected
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
                imvAddColor.visible()
                imvColor.gone()
                root.onClick { onChooseColorClick()}
            } else {
                imvAddColor.gone()
                imvColor.visible()
                    imvColor.setBackgroundColor(item.color)
                    root.onClick { onBackgroundColorClick(item.color, position) }
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