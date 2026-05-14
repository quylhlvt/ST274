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
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.databinding.ItemBottomCustomBinding

// ── NAV ADAPTER ───────────────────────────────────────────────────────────────
class NavAdapter :
    BaseAdapter<BodyPartModel, ItemBottomCustomBinding>(ItemBottomCustomBinding::inflate) {

    var posNav = 0
    var onClick: ((Int) -> Unit)? = null

    fun setPos(pos: Int) {
        val old = posNav; posNav = pos
        if (old != pos) {
            notifyItemChanged(old); notifyItemChanged(pos)
        }
    }

    override fun onBind(binding: ItemBottomCustomBinding, item: BodyPartModel, position: Int) {
        binding.apply {
            val ctx = root.context
        if (posNav == position) {
            focus.visible()
            cardLayer.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.app_color4
                )
            )
            cardLayerImg.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.app_color5
                )
            )
            cardLayer.strokeColor = ContextCompat.getColor(ctx, R.color.app_color)
        } else {
            focus.invisible()
            cardLayer.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.app_color8
                )
            )
                cardLayerImg.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.app_color9
                )
            )
            cardLayer.strokeColor = ContextCompat.getColor(ctx, R.color.app_color7)
        }
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
            .into(imvImage)

        root.setOnClickListener { onClick?.invoke(position) }
    }}
}

// ── COLOR ADAPTER ─────────────────────────────────────────────────────────────
class ColorAdapter : BaseAdapter<ColorModel, ItemColorBinding>(ItemColorBinding::inflate) {

    var posColor = 0
    var onClick: ((Int) -> Unit)? = null

    fun setPos(pos: Int) {
        val old = posColor; posColor = pos
        if (old != pos) {
            notifyItemChanged(old); notifyItemChanged(pos)
        }
    }

    override fun onBind(binding: ItemColorBinding, item: ColorModel, position: Int) {
        binding.colorSelected.isVisible = posColor == position

        val colorInt = runCatching {
            Color.parseColor(
                if (item.color.isEmpty() || item.color == "#") "#FFFFFF"
                else "#${item.color}"
            )
        }.getOrDefault(Color.WHITE)

        DrawableCompat.setTint(binding.viewColor.background.mutate(), colorInt)
        binding.root.setOnClickListener { onClick?.invoke(position) }
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
            notifyItemChanged(old); notifyItemChanged(pos)
        }
    }

    override fun onBind(binding: ItemLayerBinding, item: String, position: Int) {
        binding.apply {
            val ctx = root.context

        if (posPath == position) {
            focus.visible()

            cardLayer.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.app_color4
                )
            )
            cardLayerImg.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.app_color5
                )
            )
            cardLayer.strokeColor = ContextCompat.getColor(ctx, R.color.app_color)
        } else {
            focus.invisible()

            cardLayer.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.app_color8
                )
            )
            cardLayerImg.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    R.color.app_color9
                )
            )
            cardLayer.strokeColor = ContextCompat.getColor(ctx, R.color.app_color7)
        }
        val thumbPath = listThumb.getOrElse(position) { item }
        when (item) {
            "none" -> {
                Glide.with(imvImage).clear(imvImage)
                imvImage.setImageResource(R.drawable.ic_none)
                sflShimmer.stopShimmer()
                sflShimmer.gone()
            }

            "dice" -> {
                Glide.with(imvImage).clear(imvImage)
                imvImage.setImageResource(R.drawable.ic_dice)
                sflShimmer.stopShimmer()
                sflShimmer.gone()
            }

            else -> {
                sflShimmer.visible()
                sflShimmer.startShimmer()
                val thumbPath = listThumb.getOrElse(position) { item }
                Glide.with(imvImage)
                    .load(thumbPath)
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
                    .into(imvImage)
            }
        }
        root.setOnClickListener { onClick?.invoke(position, item) }
    }}
}