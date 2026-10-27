package com.oc.catemoji.catoc.ui.language

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.language.LanguageModel
import com.oc.catemoji.catoc.databinding.ItemLanguageBinding

class LanguageAdapter (val context: Context) : BaseAdapter<LanguageModel, ItemLanguageBinding>(
    ItemLanguageBinding::inflate
) {
    var onItemClick: ((String) -> Unit) = {}
    override fun onBind(
        binding: ItemLanguageBinding, item: LanguageModel, position: Int
    ) {
        binding.apply {
            // ✅ Dùng setImageResource thay Glide — resource tĩnh không cần Glide
            imvFlag.setImageResource(item.flag)

            tvLang.text = item.name

            // ✅ setImageResource cho radio button
            if (item.activate){
                btnRadio.setImageResource(
                    R.drawable.ic_select_lang)
                bgLang.setImageResource(R.drawable.frame_select_language)
                tvLang.setTextColor(ContextCompat.getColor(context,R.color.white))
            }else{
                btnRadio.setImageResource(
                    R.drawable.ic_un_select_lang)
                bgLang.setImageResource(R.drawable.frame_unselect_language)
                tvLang.setTextColor(ContextCompat.getColor(context, R.color.app_color))
            }



            root.onClick { onItemClick.invoke(item.code) }
        }
    }
    @SuppressLint("NotifyDataSetChanged")
    fun submitItem(position: Int) {
        val oldSelected = items.indexOfFirst { it.activate }
        items.forEach { it.activate = false }
        items[position].activate = true
        // Chỉ update 2 item thay đổi, không redraw toàn bộ list
        if (oldSelected >= 0) notifyItemChanged(oldSelected)
        notifyItemChanged(position)
    }
}