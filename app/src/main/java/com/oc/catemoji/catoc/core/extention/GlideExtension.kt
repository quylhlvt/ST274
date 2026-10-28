package com.oc.catemoji.catoc.core.extention

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.facebook.shimmer.ShimmerDrawable
import com.oc.catemoji.catoc.utils.DataLocal

private val softwareOptions = RequestOptions()
    .format(DecodeFormat.PREFER_ARGB_8888)
    .disallowHardwareConfig()

fun loadImage(context: Context, path: String, imageView: ImageView) {
    val shimmerDrawable = ShimmerDrawable().apply { setShimmer(DataLocal.shimmer) }
    Glide.with(context)
        .load(path)
        .apply(softwareOptions)
        .placeholder(shimmerDrawable)
        .error(shimmerDrawable)
        .into(imageView)
}
