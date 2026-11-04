package com.oc.catemoji.catoc.ui.main.dogSkin

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.databinding.ItemDogSkinBinding
import com.oc.catemoji.catoc.utils.DataLocal
import java.text.NumberFormat

class DogSkinAdapter(
    private val onSelect: (Int) -> Unit = {},
) : BaseAdapter<DogSkinItem, ItemDogSkinBinding>(ItemDogSkinBinding::inflate) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].skin.id.toLong()

    override fun onBind(binding: ItemDogSkinBinding, item: DogSkinItem, position: Int) {
        val context = binding.root.context
        binding.apply {
            val skinColor = ContextCompat.getColor(context, when (item.skin.type) {
                3 -> R.color._FFFF06
                2 -> R.color._FFA101
                else -> R.color._57DDFF
            })
            val bgSkinColor = ContextCompat.getDrawable(context, when (item.skin.type) {
                3 -> R.drawable.bg_title_dog_skin3
                2 -> R.drawable.bg_title_dog_skin2
                else -> R.drawable.bg_title_dog_skin1
            })
            stroker4.setGradientColors(skinColor, skinColor, skinColor)
            bgItemPogress.setBackgroundColor(skinColor)
            view10.background = bgSkinColor
            txtAvatarSkinDog.text = DataLocal.listName.getOrNull(item.skin.folderId - 1)
                ?: context.getString(R.string.dog_skin_item_title, item.skin.folderId)
            txtAvatarSkinDog.isSelected = true
            txtCoinBoneSkinDog.text = item.skin.price.toString()
            keyDogSkin.isVisible = item.locked
            view7.isVisible = !item.locked
            selectDogSkin.isVisible = item.selected
            root.isSelected = item.selected
            root.isEnabled = !item.locked || item.canAfford
            root.onClick { if (root.isEnabled) onSelect(item.skin.id) }
            txtLock.setText(when {
                item.selected -> R.string.dog_skin_equipped
                item.locked -> R.string.locked
                else -> R.string.dog_skin_unlocked
            })
            txtLock.setBackgroundColor(ContextCompat.getColor(context, when {
                item.selected -> R.color._22C55E
                item.locked -> R.color._868686
                else -> R.color.white
            }))
            txtLock.setTextColor(ContextCompat.getColor(context,
                if (item.locked || item.selected) R.color.white else R.color.app_color))
            txtProgressPercent.text = NumberFormat.getPercentInstance().format(item.progress / 100.0)
            txtMaxProgress.setText(R.string.max_level_reached)
            txtMaxProgress.isVisible = !item.locked && item.isMaxLevel
            txtMaxProgress.isSelected = true
            val progressParams = view12.layoutParams as ConstraintLayout.LayoutParams
            progressParams.guidePercent = item.progress / 100f
            view12.layoutParams = progressParams
            imgBgRankDogSkin.setImageResource(when (item.skin.type) {
                3 -> R.drawable.img_bg_rank_dog_skin3
                2 -> R.drawable.img_bg_rank_dog_skin2
                else -> R.drawable.img_bg_rank_dog_skin1
            })
            imgRankDogSkin.setImageResource(when (item.skin.type) {
                3 -> R.drawable.img_rank_dog_skin3
                2 -> R.drawable.img_rank_dog_skin2
                else -> R.drawable.img_rank_dog_skin1
            })
            Glide.with(imgDogSkin).load(item.image)
                .placeholder(R.drawable.image_avatar_infor)
                .error(R.drawable.image_avatar_infor)
                .into(imgDogSkin)
        }
    }
}
