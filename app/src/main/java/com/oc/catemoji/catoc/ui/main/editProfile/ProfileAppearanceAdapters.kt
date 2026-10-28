package com.oc.catemoji.catoc.ui.main.editProfile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.loadImage
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.ItemAvatarBinding
import com.oc.catemoji.catoc.databinding.ItemFrameAvatarBinding

data class ProfileAppearanceItem(val image: String, val selected: Boolean, val enabled: Boolean, val unlock: Boolean)

abstract class ProfileAppearanceAdapter<VB : ViewBinding>(
    inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    private val isFrame: Boolean,
    private val onSelect: (String) -> Unit,
) : BaseAdapter<ProfileAppearanceItem, VB>(inflate) {
    fun render(images: List<String>, selected: String, enabled: Boolean, unlockedImages: Set<String>) {
        val entries = images.map { ProfileAppearanceItem(it, it == selected, enabled, it in unlockedImages) }
        if (items != entries) submitList(entries)
    }

    override fun onBind(binding: VB, item: ProfileAppearanceItem, position: Int) {
        val image = binding.root.findViewById<ImageView>(R.id.imgAvatar)
        Glide.with(image).clear(image)
        image.setImageDrawable(null)
        if (isFrame) {
            Glide.with(image).load(item.image).dontAnimate().into(image)
        } else {
            loadImage(binding.root.context, item.image, image)
        }
        binding.root.findViewById<View>(R.id.stokerSelect).isVisible = item.selected
        binding.root.findViewById<View>(R.id.key).isVisible = !item.unlock
        binding.root.isSelected = item.selected
        binding.root.isEnabled = item.enabled && item.unlock
        binding.root.contentDescription = binding.root.context.getString(
            when {
                isFrame && !item.unlock -> R.string.profile_frame_locked_option
                !item.unlock -> R.string.profile_avatar_locked_option
                isFrame -> R.string.profile_frame_option
                else -> R.string.profile_avatar_option
            }, position + 1,
        )
        binding.root.onClick { if (item.enabled && item.unlock) onSelect(item.image) }
    }
}

class ProfileAvatarAdapter(onSelect: (String) -> Unit) : ProfileAppearanceAdapter<ItemAvatarBinding>(
    ItemAvatarBinding::inflate, false, onSelect,
)

class ProfileFrameAdapter(onSelect: (String) -> Unit) : ProfileAppearanceAdapter<ItemFrameAvatarBinding>(
    ItemFrameAvatarBinding::inflate, true, onSelect,
)
