package com.oc.catemoji.catoc.ui.main.profile

import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.data.datalocal.room.entity.AchievementEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.model.statistics.Statistics
import com.oc.catemoji.catoc.databinding.ItemAchievermentBinding
import com.oc.catemoji.catoc.databinding.ItemHistoryBinding
import com.oc.catemoji.catoc.databinding.ItemStatisBinding
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

class ProfileStatisticAdapter : BaseAdapter<Statistics, ItemStatisBinding>(
    ItemStatisBinding::inflate,
) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].name.toLong()

    override fun onBind(binding: ItemStatisBinding, item: Statistics, position: Int) {
        binding.titleStatis.setText(item.name)
        binding.titleStatis.isSelected = true
        binding.txtCountData.text = if (item.name == R.string.rate) {
            NumberFormat.getPercentInstance().apply { maximumFractionDigits = 2 }
                .format(item.count / 100)
        } else {
            NumberFormat.getIntegerInstance().format(item.count)
        }
        binding.imageStatis.setImageResource(item.image)
    }
}

class ProfileHistoryAdapter : BaseAdapter<HistoryEntity, ItemHistoryBinding>(ItemHistoryBinding::inflate) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun onBind(binding: ItemHistoryBinding, item: HistoryEntity, position: Int) {
        binding.txtBattleName.text = item.nameBatter
        binding.txtBattleDate.text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.date))
        val lost = !item.typePlay
        binding.txtTypeWar.setText(if (item.typePlay) R.string.victory else R.string.profile_defeat)
        binding.root.setBackgroundColor(ContextCompat.getColor(
            binding.root.context, if (lost) R.color.lost else R.color.win,
        ))
        Glide.with(binding.imgAvatar1).load(item.imageAvatar1)
            .placeholder(R.drawable.image_avatar_infor).error(R.drawable.image_avatar_infor)
            .into(binding.imgAvatar1)
        Glide.with(binding.imgAvatar2).load(item.imageAvatar2)
            .placeholder(R.drawable.image_avatar_infor).error(R.drawable.image_avatar_infor)
            .into(binding.imgAvatar2)
    }
}

class ProfileAchievementAdapter : BaseAdapter<AchievementEntity, ItemAchievermentBinding>(
    ItemAchievermentBinding::inflate,
) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id.toLong()

    override fun onBind(binding: ItemAchievermentBinding, item: AchievementEntity, position: Int) {
        binding.titleStatis.text = item.title
        binding.titleStatis.isSelected = true
        Glide.with(binding.imageAchiever).load(item.image)
            .placeholder(R.drawable.img_rank).error(R.drawable.img_rank)
            .into(binding.imageAchiever)
    }
}
