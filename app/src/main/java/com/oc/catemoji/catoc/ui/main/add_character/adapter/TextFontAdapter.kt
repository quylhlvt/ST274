package com.oc.catemoji.catoc.ui.main.add_character.adapter

import android.annotation.SuppressLint
import android.content.Context
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.setFont
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.databinding.ItemFontBinding


class TextFontAdapter(val context: Context) : BaseAdapter<SelectedAddModel, ItemFontBinding>(ItemFontBinding::inflate) {
    var onTextFontClick: ((Int, Int) -> Unit) = { _, _ -> }
    private var currentSelected = 0

    override fun onBind(binding: ItemFontBinding, item: SelectedAddModel, position: Int) {
        binding.apply {
            val res = if (item.isSelected) R.drawable.bg_100linear_stroker_white else R.drawable.bg_100_stroker_appcolor
            vFocus.setBackgroundResource(res)

            tvFont.setFont(item.color)
            val (color, elevation) = if (item.isSelected) R.color.app_color to 6f else R.color.app_color9 to 0f
            tvFont.setTextColor(context.getColor(color))
            cvMain.cardElevation = elevation
            root.onClick { onTextFontClick.invoke(item.color, position) }
        }
    }

    fun submitItem(position: Int, list: ArrayList<SelectedAddModel>) {
        if (position != currentSelected) {
            items.clear()
            items.addAll(list)

            notifyItemChanged(currentSelected)
            notifyItemChanged(position)

            currentSelected = position
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitListReset(list: ArrayList<SelectedAddModel>){
        items.clear()
        items.addAll(list)
        currentSelected = 0
        notifyDataSetChanged()
    }
}