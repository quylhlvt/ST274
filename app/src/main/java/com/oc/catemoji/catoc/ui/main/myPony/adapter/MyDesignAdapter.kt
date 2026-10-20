package com.oc.catemoji.catoc.ui.main.myPony.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewbinding.ViewBinding
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.mypony.MyAlbumModel
import com.oc.catemoji.catoc.databinding.ItemMyDesignBinding
import com.oc.catemoji.catoc.databinding.ItemMyDiyBinding

class MyDesignAdapter() : BaseAdapter<MyAlbumModel, ViewBinding>(ItemMyDesignBinding::inflate) {
    var onItemClick: ((String) -> Unit) = {}
    var onLongClick: ((Int) -> Unit) = {}
    var onItemTick: ((Int) -> Unit) = {}

    var onDeleteClick: ((String) -> Unit) = {}
    var onMoreClick: ((View, String) -> Unit) = { _, _ -> }
    var isDiy: Boolean = false

    override fun getItemViewType(position: Int): Int = if (isDiy) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return if (viewType == 1) {
            BaseViewHolder(ItemMyDiyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            super.onCreateViewHolder(parent, viewType)
        }
    }

    override fun onBind(binding: ViewBinding, item: MyAlbumModel, position: Int) {
        when (binding) {
            is ItemMyDiyBinding -> bindItem(
                binding.root, binding.imvImage, binding.btnSelect, binding.btnMore, item, position, true
            )
            is ItemMyDesignBinding -> bindItem(
                binding.root, binding.imvImage, binding.btnSelect, binding.btnDelete, item, position, false
            )
        }
    }

    private fun bindItem(
        root: View,
        imvImage: ImageView,
        btnSelect: ImageView,
        btnAction: ImageView,
        item: MyAlbumModel,
        position: Int,
        showMore: Boolean
    ) {
        if (imvImage.tag != item.path) {
            imvImage.tag = item.path
            loadImage(root.context, item.path, imvImage)
        }

        if (item.isShowSelection) {
            btnSelect.visible()
            btnAction.gone()
        } else {
            btnSelect.gone()
            btnAction.visible()
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

        btnAction.onClick {
            if (showMore) onMoreClick.invoke(btnAction, item.path)
            else onDeleteClick.invoke(item.path)
        }
        btnSelect.onClick { onItemTick.invoke(position) }
    }
}
