package com.oc.catemoji.catoc.utils
import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class AppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Memory cache: 50MB
        builder.setMemoryCache(LruResourceCache(50L * 1024 * 1024))
        // Disk cache: 200MB — đủ cache toàn bộ ảnh template API
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 200L * 1024 * 1024))
    }
}