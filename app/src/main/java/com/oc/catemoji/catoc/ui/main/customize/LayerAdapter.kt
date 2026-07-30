package com.oc.catemoji.catoc.ui.main.customize

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseAdapter
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.ColorModel
import com.oc.catemoji.catoc.databinding.ItemColorBinding
import com.oc.catemoji.catoc.databinding.ItemLayerBinding
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.DataSource
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.databinding.ItemBottomCustomBinding
import com.oc.catemoji.catoc.utils.DataLocal

// ── NAV ADAPTER ───────────────────────────────────────────────────────────────
class NavAdapter :
    BaseAdapter<BodyPartModel, ItemBottomCustomBinding>(ItemBottomCustomBinding::inflate) {

    var posNav = 0
    var onClick: ((Int) -> Unit)? = null

    fun setPos(pos: Int) {
        val old = posNav; posNav = pos
        val lastIndex = itemCount - 1
        if (old != pos && lastIndex >= 0) {
            if (old in 0..lastIndex) notifyItemChanged(old, PAYLOAD_SELECTION)
            if (pos in 0..lastIndex) notifyItemChanged(pos, PAYLOAD_SELECTION)
        }
    }

    private fun updateSelection(binding: ItemBottomCustomBinding, position: Int) {
        if (posNav == position) binding.focus.visible() else binding.focus.gone()
    }

    override fun onBind(binding: ItemBottomCustomBinding, item: BodyPartModel, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

        binding.apply {
            val ctx = root.context
        updateSelection(binding, position)
        if (imvImage.tag != item.nav) {
            sflShimmer.visible()
            sflShimmer.startShimmer()
            imvImage.tag = item.nav
            Glide.with(imvImage)
                .load(item.nav)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(256)
                .dontAnimate()
                .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?,
                    target: Target<Drawable>?, isFirstResource: Boolean
                ): Boolean {
                    sflShimmer.stopShimmer()
                    sflShimmer.gone()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?, model: Any?,
                    target: Target<Drawable>?, dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    sflShimmer.stopShimmer()
                    sflShimmer.gone()
                    return false
                }
                })
                .placeholder(shimmerDrawable)
                .into(imvImage)
        } else {
            sflShimmer.stopShimmer()
            sflShimmer.gone()
        }

        root.setOnClickListener { onClick?.invoke(position) }
    }}

    override fun onBindPayload(
        binding: ItemBottomCustomBinding,
        item: BodyPartModel,
        position: Int,
        payloads: List<Any>
    ) = updateSelection(binding, position)

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}

// ── COLOR ADAPTER ─────────────────────────────────────────────────────────────
class ColorAdapter : BaseAdapter<ColorModel, ItemColorBinding>(ItemColorBinding::inflate) {

    var posColor = 0
    var onClick: ((Int) -> Unit)? = null

    fun setPos(pos: Int) {
        val old = posColor; posColor = pos
        if (old != pos) {
            if (old in items.indices) notifyItemChanged(old, PAYLOAD_SELECTION)
            if (pos in items.indices) notifyItemChanged(pos, PAYLOAD_SELECTION)
        }
    }

    private fun updateSelection(binding: ItemColorBinding, position: Int) {
        binding.colorSelected.isVisible = posColor == position
    }

    override fun onBind(binding: ItemColorBinding, item: ColorModel, position: Int) {
        updateSelection(binding, position)

        val colorInt = runCatching {
            Color.parseColor(
                if (item.color.isEmpty() || item.color == "#") "#FFFFFF"
                else "#${item.color}"
            )
        }.getOrDefault(Color.WHITE)

        DrawableCompat.setTint(binding.viewColor.background.mutate(), colorInt)
        binding.root.setOnClickListener { onClick?.invoke(position) }
    }

    override fun onBindPayload(
        binding: ItemColorBinding,
        item: ColorModel,
        position: Int,
        payloads: List<Any>
    ) = updateSelection(binding, position)

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}

// ── PART ADAPTER ──────────────────────────────────────────────────────────────
class PartAdapter : BaseAdapter<String, ItemLayerBinding>(ItemLayerBinding::inflate) {

    var posPath: Int = 0
    var listThumb: List<String> = emptyList()
    var onClick: ((Int, String) -> Unit)? = null

    fun setPos(pos: Int) {
        val old = posPath; posPath = pos
        if (old != pos) {
            if (old in items.indices) notifyItemChanged(old, PAYLOAD_SELECTION)
            if (pos in items.indices) notifyItemChanged(pos, PAYLOAD_SELECTION)
        }
    }

    private fun updateSelection(binding: ItemLayerBinding, position: Int) {
        if (posPath == position) binding.focus.visible() else binding.focus.gone()
    }

    override fun onBind(binding: ItemLayerBinding, item: String, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

        binding.apply {
            val ctx = root.context

        updateSelection(binding, position)
        val thumbPath = listThumb.getOrElse(position) { item }
        when (item) {
            "none" -> {
                Glide.with(imvImage).clear(imvImage)
                imvImage.tag = "none"
                imvImage.setImageResource(R.drawable.ic_none)

            }

            "dice" -> {
                Glide.with(imvImage).clear(imvImage)
                imvImage.tag = "dice"
                imvImage.setImageResource(R.drawable.ic_dice)

            }

            else -> {
                val thumbPath = listThumb.getOrElse(position) { item }
                if (imvImage.tag != thumbPath) {
                    imvImage.tag = thumbPath
                    Glide.with(imvImage)
                        .load(thumbPath)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .override(256)
                        .dontAnimate()
                        .placeholder(shimmerDrawable)
                        .into(imvImage)
                }
            }
        }
        root.setOnClickListener { onClick?.invoke(position, item) }
    }}

    override fun onBindPayload(
        binding: ItemLayerBinding,
        item: String,
        position: Int,
        payloads: List<Any>
    ) = updateSelection(binding, position)

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}
