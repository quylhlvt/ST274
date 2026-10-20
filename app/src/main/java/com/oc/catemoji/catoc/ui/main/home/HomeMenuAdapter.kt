package com.oc.catemoji.catoc.ui.main.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.databinding.ItemHomeMenuBinding

enum class HomeMenuAction {
    CREATE, DIY,Pause,STUDIO, COSPLAY, RANDOM, MY_ALBUM
}

data class HomeMenuItem(
    @StringRes val titleRes: Int,
    val action: HomeMenuAction
)

class HomeMenuAdapter(
    private val onItemClick: (HomeMenuAction) -> Unit
) : RecyclerView.Adapter<HomeMenuAdapter.HomeMenuViewHolder>() {

    private val items = listOf(
        HomeMenuItem(R.string.pony_maker, HomeMenuAction.CREATE),
        HomeMenuItem(R.string.diy_pet_language, HomeMenuAction.DIY),
        HomeMenuItem(R.string.pause_oc_challenge, HomeMenuAction.Pause),
        HomeMenuItem(R.string.cat_studio, HomeMenuAction.STUDIO),
        HomeMenuItem(R.string.cosplay, HomeMenuAction.COSPLAY),
        HomeMenuItem(R.string.cat_random, HomeMenuAction.RANDOM),
        HomeMenuItem(R.string.my_creation, HomeMenuAction.MY_ALBUM)
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeMenuViewHolder {
        val binding = ItemHomeMenuBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HomeMenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HomeMenuViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HomeMenuViewHolder(
        private val binding: ItemHomeMenuBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeMenuItem) {
            binding.tvTitle.isSelected = true
            binding.tvTitle.setText(item.titleRes)
            binding.root.setOnClickListener { onItemClick(item.action) }
        }
    }
}
