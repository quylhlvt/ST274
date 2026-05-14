package com.oc.catemoji.catoc.ui.main.myPony.adapter

import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.mypony.MyAlbumModel
import com.oc.catemoji.catoc.databinding.ItemMyDesignBinding

class MyDesignAdapter() : BaseAdapter<MyAlbumModel, ItemMyDesignBinding>(ItemMyDesignBinding::inflate) {
    var onItemClick: ((String) -> Unit) = {}
    var onLongClick: ((Int) -> Unit) = {}
    var onItemTick: ((Int) -> Unit) = {}

    var onDeleteClick: ((String) -> Unit) = {}

    override fun onBind(binding: ItemMyDesignBinding, item: MyAlbumModel, position: Int) {
        binding.apply {
            loadImage(root, item.path, imvImage)

            if (item.isShowSelection) {
                btnSelect.visible()
                btnDelete.gone()
            } else {
                btnSelect.gone()
                btnDelete.visible()
            }

            btnSelect.setImageResource(
                if (item.isSelected) R.drawable.ic_selected else R.drawable.ic_not_select
            )

            // Click luôn navigate
            root.onClick { onItemClick.invoke(item.path) }

            root.setOnLongClickListener {
                if (items.any { it.isShowSelection }) return@setOnLongClickListener false
                onLongClick.invoke(position)
                true
            }

            btnDelete.onClick { onDeleteClick.invoke(item.path) }
            btnSelect.onClick { onItemTick.invoke(position) }
        }
    }
}