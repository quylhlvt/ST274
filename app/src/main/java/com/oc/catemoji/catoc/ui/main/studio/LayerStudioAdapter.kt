package com.oc.catemoji.catoc.ui.main.studio

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
import com.oc.catemoji.catoc.databinding.ItemBottomCustomStudioBinding
import com.oc.catemoji.catoc.databinding.ItemStudioLayerBinding
import com.oc.catemoji.catoc.utils.DataLocal

// ── NAV ADAPTER ───────────────────────────────────────────────────────────────
class NavStudioAdapter :
    BaseAdapter<BodyPartModel, ItemBottomCustomStudioBinding>(ItemBottomCustomStudioBinding::inflate) {

    var posNav = 0
    var onClick: ((Int) -> Unit)? = null

    fun setPos(pos: Int, recyclerView: RecyclerView? = null) {
        val old = posNav; posNav = pos
        if (old != pos) {
            recyclerView?.let {
                if (old in items.indices) {
                    findBinding(it, old)?.let { binding -> updateFocus(binding, old) }
                }
                if (pos in items.indices) {
                    findBinding(it, pos)?.let { binding -> updateFocus(binding, pos) }
                }
            }
            if (old in items.indices) notifyItemChanged(old, PAYLOAD_FOCUS)
            if (pos in items.indices) notifyItemChanged(pos, PAYLOAD_FOCUS)
        }
    }

    private fun updateFocus(binding: ItemBottomCustomStudioBinding, position: Int) {
        binding.focus.visibility = android.view.View.VISIBLE
        binding.focus.alpha = if (posNav == position) 1f else 0f
    }

    override fun onBind(binding: ItemBottomCustomStudioBinding, item: BodyPartModel, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

        binding.apply {
            updateFocus(binding, position)
            if (imvImage.tag != item.nav) {
                imvImage.tag = item.nav
                Glide.with(imvImage)
                    .load(item.nav)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .override(128)
                    .dontAnimate()
                    .placeholder(shimmerDrawable)
                    .into(imvImage)
            }

            root.setOnClickListener { onClick?.invoke(position) }
        }
    }

    override fun onBindPayload(
        binding: ItemBottomCustomStudioBinding,
        item: BodyPartModel,
        position: Int,
        payloads: List<Any>
    ) {
        updateFocus(binding, position)
    }

    private companion object {
        const val PAYLOAD_FOCUS = "focus"
    }
}


// ── PART ADAPTER ──────────────────────────────────────────────────────────────
class PartStudioAdapter : BaseAdapter<String, ItemStudioLayerBinding>(ItemStudioLayerBinding::inflate) {

    private var selectedPath: String? = null
    var listThumb: List<String> = emptyList()
    var onClick: ((Int, String) -> Unit)? = null

    fun setSelectedPath(path: String?) {
        if (selectedPath == path) return
        val oldIndex = items.indexOf(selectedPath)
        selectedPath = path
        val newIndex = items.indexOf(path)
        if (oldIndex >= 0) notifyItemChanged(oldIndex, PAYLOAD_FOCUS)
        if (newIndex >= 0) notifyItemChanged(newIndex, PAYLOAD_FOCUS)
    }

    fun clearSelection() {
        if (selectedPath == null) return
        val oldIndex = items.indexOf(selectedPath)
        selectedPath = null
        if (oldIndex >= 0) notifyItemChanged(oldIndex, PAYLOAD_FOCUS)
    }

    override fun onBind(binding: ItemStudioLayerBinding, item: String, position: Int) {
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

        binding.apply {
            updateFocus(binding, item)

            val thumbPath = listThumb.getOrElse(position) { item }
            if (imvImage.tag != thumbPath) {
                imvImage.tag = thumbPath
                Glide.with(imvImage)
                    .load(thumbPath)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(LAYER_THUMB_SIZE_PX, LAYER_THUMB_SIZE_PX)
                    .dontAnimate()
                    .placeholder(shimmerDrawable)
                    .into(imvImage)
            }

            root.setOnClickListener { onClick?.invoke(position, item) }
        }
    }

    private fun updateFocus(binding: ItemStudioLayerBinding, item: String) {
        binding.focus.visibility = android.view.View.VISIBLE
        binding.focus.alpha = if (selectedPath == item) 1f else 0f
    }

    override fun onBindPayload(
        binding: ItemStudioLayerBinding,
        item: String,
        position: Int,
        payloads: List<Any>
    ) {
        updateFocus(binding, item)
    }

    private companion object {
        const val PAYLOAD_FOCUS = "focus"
        const val LAYER_THUMB_SIZE_PX = 192
    }
}
