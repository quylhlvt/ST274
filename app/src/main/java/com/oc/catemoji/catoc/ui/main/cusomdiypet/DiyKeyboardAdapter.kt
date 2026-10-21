package com.oc.catemoji.catoc.ui.main.cusomdiypet

import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import androidx.core.view.isVisible
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.ItemKeybroadBinding

class DiyKeyboardAdapter(
    private val onItemClick: (String, String) -> Unit,
    private val onDeleteClick: () -> Unit = {}
) : BaseAdapter<String, ItemKeybroadBinding>(ItemKeybroadBinding::inflate) {

    private var showDeleteAtEnd = false
    private var keyLabels: List<String> = emptyList()

    fun setKeyLabels(labels: List<String>) {
        keyLabels = labels
    }

    fun submitKeyboardList(list: List<String>, showDeleteAtEnd: Boolean = false) {
        this.showDeleteAtEnd = showDeleteAtEnd
        submitList(list)
    }

    override fun onBind(binding: ItemKeybroadBinding, item: String, position: Int) {
        val isDelete = showDeleteAtEnd && position == items.lastIndex
        binding.btnDeleteKey.isVisible = isDelete
        binding.btnKeyBroad.alpha = if (isDelete) 0f else 1f

        if (isDelete) {
            binding.txtKey.text = ""
            binding.root.onClick { onDeleteClick() }
            return
        }

        binding.imgKey.tag = item
        val keyLabel = keyLabels.getOrNull(position).orEmpty()
        binding.txtKey.text = keyLabel
        Glide.with(binding.imgKey)
            .load(item)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(96, 96)
            .dontAnimate()
            .into(binding.imgKey)
        binding.root.onClick { onItemClick(item, keyLabel) }
    }
}
