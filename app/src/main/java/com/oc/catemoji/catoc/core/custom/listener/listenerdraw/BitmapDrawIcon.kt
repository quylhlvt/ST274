package com.oc.catemoji.catoc.core.custom.listener.listenerdraw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.custom.DrawKey
import com.oc.catemoji.catoc.core.custom.DrawView
import com.oc.catemoji.catoc.core.custom.DrawableDraw

class BitmapDrawIcon(drawable: Drawable?, @Gravity gravity: Int) : DrawableDraw(drawable!!, "nbhieu"),
    DrawEvent {
    @IntDef(*[DrawKey.TOP_LEFT, DrawKey.RIGHT_TOP, DrawKey.LEFT_BOTTOM, DrawKey.RIGHT_BOTTOM])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Gravity

    var radius = DrawKey.DEFAULT_RADIUS
    var x = 0f
    var y = 0f

    @get:Gravity
    @Gravity
    var positionDefault = DrawKey.TOP_LEFT
    var event: DrawEvent? = null

    init {
        positionDefault = gravity
    }

    override fun onActionDown(tattooView: DrawView?, event: MotionEvent?) {
        if (this.event != null) {
            this.event!!.onActionDown(tattooView, event)
        }
    }

    override fun onActionMove(tattooView: DrawView?, event: MotionEvent?) {
        if (this.event != null) {
            this.event!!.onActionMove(tattooView, event)
        }
    }

    override fun onActionUp(tattooView: DrawView?, event: MotionEvent?) {
        if (this.event != null) {
            this.event!!.onActionUp(tattooView, event)
        }
    }


    fun draw(canvas: Canvas, paint: Paint,context: Context) {
//        paint.isAntiAlias = true
//        paint.style = Paint.Style.FILL
//        paint.alpha = 255
//        val gradient = LinearGradient(
//            x, y -radius,
//            x, y -radius ,
//            intArrayOf(
//                "#2AACEF".toColorInt(),
//                "#FEE168".toColorInt()
//            ),
//            null,
//            Shader.TileMode.CLAMP
//        )
//
//        paint.shader = gradient
        val cornerRadius = 40f
        paint.color= ContextCompat.getColor(context, R.color.color_draw)
//        canvas.drawCircle(x, y, radius, paint)


//        paint.shader = null // reset để tránh ảnh hưởng super.draw
        super.draw(canvas)
    }
}

