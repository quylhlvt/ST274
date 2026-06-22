package com.oc.catemoji.catoc.core.extention

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import coil.load
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.utils.DataLocal
import com.facebook.shimmer.ShimmerDrawable
import java.io.File

// ✅ Options dùng chung — software bitmap, tránh hardware bitmap error
private val softwareOptions = RequestOptions()
    .format(DecodeFormat.PREFER_ARGB_8888)
    .disallowHardwareConfig()

fun ImageView.loadImage(imgResource: Any? = null) {
    val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }
    this.load(imgResource) {
        placeholder(shimmerDrawable)
        error(shimmerDrawable)
    }
}

fun loadImage(
    context: Context,
    path: String,
    imageView: ImageView,
    isLoadShimmer: Boolean = true
) {
    val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }
    Glide.with(context)
        .load(path)
        .apply(softwareOptions)  // ✅
        .placeholder(shimmerDrawable)
        .error(shimmerDrawable)
        .into(imageView)
}

fun loadImage(
    viewGroup: ViewGroup,
    path: String,
    imageView: ImageView,
    isLoadShimmer: Boolean = true
) {
    val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }
    Glide.with(viewGroup)
        .load(path)
        .apply(softwareOptions)  // ✅
        .placeholder(shimmerDrawable)
        .into(imageView)
}
fun loadImage(
    viewGroup: ViewGroup,
    path: Int,
    imageView: ImageView,
) {
    val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

    Glide.with(viewGroup)
        .load(path)
        .apply(softwareOptions)
        .placeholder(shimmerDrawable)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // ✅ luôn cache
        .override(96, 96)                              // ✅ size nhỏ hơn cho flag icons
        .into(imageView)
}

fun loadImage(
    viewGroup: ViewGroup,
    path: Int,
    imageView: ImageView,
    isLoadShimmer: Boolean = true
) {
    val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }
    if (isLoadShimmer) {
        Glide.with(viewGroup)
            .load(path)
            .apply(softwareOptions)  // ✅
            .placeholder(shimmerDrawable)
            .error(shimmerDrawable)
            .into(imageView)
    } else {
        Glide.with(viewGroup)
            .load(path)
            .apply(softwareOptions)  // ✅
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(imageView)
    }
}

fun loadImage(
    path: Any,
    imageView: ImageView,
    onShowLoading: (() -> Unit)? = null,
    onDismissLoading: (() -> Unit)? = null
) {
    val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }

    onShowLoading?.invoke()
    Glide.with(imageView.context)
        .load(path)
        .placeholder(shimmerDrawable)
        .apply(softwareOptions)  // ✅
        .listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?,
                target: Target<Drawable?>, isFirstResource: Boolean
            ): Boolean {
                onDismissLoading?.invoke()
                return false
            }
            override fun onResourceReady(
                resource: Drawable, model: Any,
                target: Target<Drawable?>?, dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                onDismissLoading?.invoke()
                return false
            }
        })
        .into(imageView)
}

@SuppressLint("CheckResult")
fun ImageView.loadImageFromFile(path: String) {
    val file = File(path)
    Glide.with(context)
        .load(file)
        .apply(softwareOptions)  // ✅
        .signature(ObjectKey(file.lastModified()))
        .into(this)
}

fun loadThumbnail(view: ImageView, url: String) {
    val file = File(url)
    Glide.with(view.context)
        .asBitmap()
        .load(file)
        .apply(softwareOptions)  // ✅
        .frame(1000000)
        .into(view)
}
fun ImageView.loadFromAsset(assetPath: String) {
    Glide.with(this)
        .load(Uri.parse(assetPath))
        .override(120, 120)                           // ← QUAN TRỌNG NHẤT
        .diskCacheStrategy(DiskCacheStrategy.ALL)     // ← cache, scroll lại không decode
        .centerCrop()
        .placeholder(ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) })
        .error(R.drawable.logo_app)
        .into(this)
}