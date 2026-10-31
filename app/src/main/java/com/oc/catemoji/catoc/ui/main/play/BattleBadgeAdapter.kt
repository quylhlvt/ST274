package com.oc.catemoji.catoc.ui.main.play

import com.bumptech.glide.Glide
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.data.datalocal.room.entity.AchievementEntity
import com.oc.catemoji.catoc.databinding.ItemAchiBinding

class BattleBadgeAdapter : BaseAdapter<AchievementEntity, ItemAchiBinding>(ItemAchiBinding::inflate) {
    override fun onBind(binding: ItemAchiBinding, item: AchievementEntity, position: Int) {
        binding.namne.text = item.title
        Glide.with(binding.avatarAchi).load(item.image).placeholder(R.drawable.achi1)
            .error(R.drawable.achi1).into(binding.avatarAchi)
    }
}
