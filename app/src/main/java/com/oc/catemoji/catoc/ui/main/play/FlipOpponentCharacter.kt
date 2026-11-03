package com.oc.catemoji.catoc.ui.main.play

import android.graphics.Bitmap
import android.graphics.Canvas
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/** Mirrors only the battle art; view scaling remains owned by BattleAvatarGrowth. */
internal object FlipOpponentCharacter : BitmapTransformation() {
    private val cacheKey = "com.oc.catemoji.catoc.play.FlipOpponentCharacter.v1".toByteArray(Charsets.UTF_8)

    override fun transform(pool: BitmapPool, source: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val result = pool.get(source.width, source.height, Bitmap.Config.ARGB_8888)
        result.setHasAlpha(source.hasAlpha())
        result.density = source.density
        Canvas(result).apply {
            scale(-1f, 1f, source.width / 2f, source.height / 2f)
            drawBitmap(source, 0f, 0f, null)
        }
        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(cacheKey)
    }

    override fun equals(other: Any?): Boolean = other is FlipOpponentCharacter

    override fun hashCode(): Int = cacheKey.contentHashCode()
}
