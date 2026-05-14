package com.oc.catemoji.catoc.ui.main.myPony.adapter

import android.content.Context
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.mypony.MyAlbumModel
import com.oc.catemoji.catoc.databinding.ItemMyAlbumBinding


class MyAvatarAdapter(val context: Context) :
    BaseAdapter<MyAlbumModel, ItemMyAlbumBinding>(ItemMyAlbumBinding::inflate) {
    var onItemClick: ((MyAlbumModel) -> Unit) = {}
    var onLongClick: ((Int) -> Unit) = {}
    var onItemTick: ((Int) -> Unit) = {}

    var onEditClick: ((String) -> Unit) = {}
    var onDeleteClick: ((String) -> Unit) = {}

    override fun onBind(binding: ItemMyAlbumBinding, item: MyAlbumModel, position: Int) {
        binding.apply {
            loadImage(root, item.path, imvImage)

            if (item.isShowSelection) {
                btnSelect.visible()
                btnEdit.gone()
                btnDelete.gone()
            } else {
                btnSelect.gone()
                btnEdit.visible()
                btnDelete.visible()
            }

            btnSelect.setImageResource(
                if (item.isSelected) R.drawable.ic_selected else R.drawable.ic_not_select
            )

            // Click luôn navigate, không check selection mode
            root.onClick { onItemClick.invoke(item) }

            root.setOnLongClickListener {
                if (items.any { it.isShowSelection }) return@setOnLongClickListener false
                onLongClick.invoke(position)
                true
            }

            btnEdit.onClick { onEditClick.invoke(item.idEdit) }
            btnDelete.onClick { onDeleteClick.invoke(item.path) }
            btnSelect.onClick { onItemTick.invoke(position) } // chỉ tick button mới toggle
        }
    }
}