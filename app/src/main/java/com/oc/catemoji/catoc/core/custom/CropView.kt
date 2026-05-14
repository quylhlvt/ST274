package com.oc.catemoji.catoc.core.custom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt


class CropView : AppCompatImageView {

    private var paint = Paint()
    private val initial_size = 500
    private var leftTop: Point? = null
    private var rightBottom: Point? = null
    private var center: Point? = null
    private var previous: Point? = null

    private val DRAG = 0
    private val LEFT = 1
    private val TOP = 2
    private val RIGHT = 3
    private val BOTTOM = 4

    private var imageScaledWidth = 0
    private var imageScaledHeight = 0

    // Adding parent class constructors
    constructor(context: Context) : this(context, null) {
        initCropView()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initCropView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initCropView()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (leftTop!!.equals(0, 0)) resetPoints()
        canvas.drawRect(leftTop!!.x.toFloat(), leftTop!!.y.toFloat(), rightBottom!!.x.toFloat(), rightBottom!!.y.toFloat(), paint)
    }

    @SuppressLint("ClickableViewAccessibility") override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> previous!!.set(event.x.toInt(), event.y.toInt())
            MotionEvent.ACTION_MOVE -> if (isActionInsideRectangle(event.x, event.y)) {
                adjustRectangle(event.x.toInt(), event.y.toInt())
                invalidate() // redraw rectangle
                previous!!.set(event.x.toInt(), event.y.toInt())
            }
            MotionEvent.ACTION_UP -> previous = Point()
        }
        return true
    }

    private fun initCropView() {
        paint.color = Color.BLUE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        leftTop = Point()
        rightBottom = Point()
        center = Point()
        previous = Point()
    }
    fun resetPoints() {
        center!!.set(width / 2, height / 2)
        leftTop!![(width - initial_size) / 2] = (height - initial_size) / 2
        rightBottom!!.set(leftTop!!.x + initial_size, leftTop!!.y + initial_size)
    }

    private fun isActionInsideRectangle(x: Float, y: Float): Boolean {
        val buffer = 10
        return x >= leftTop!!.x - buffer && x <= rightBottom!!.x + buffer && y >= leftTop!!.y - buffer && y <= rightBottom!!.y + buffer
    }

    private fun isInImageRange(point: PointF): Boolean {
        // Get image matrix values and place them in an array
        val f = FloatArray(9)
        imageMatrix.getValues(f)

        // Calculate the scaled dimensions
        imageScaledWidth = (drawable.intrinsicWidth * f[Matrix.MSCALE_X]).roundToInt()
        imageScaledHeight = (drawable.intrinsicHeight * f[Matrix.MSCALE_Y]).roundToInt()
        return point.x >= center!!.x - imageScaledWidth / 2 && point.x <= center!!.x + imageScaledWidth / 2 && point.y >= center!!.y - imageScaledHeight / 2 && point.y <= center!!.y + imageScaledHeight / 2
    }

    private fun adjustRectangle(x: Int, y: Int) {
        val movement: Int
        when (getAffectedSide(x.toFloat(), y.toFloat())) {
            LEFT -> {
                movement = x - leftTop!!.x
                if (isInImageRange(PointF((leftTop!!.x + movement).toFloat(), (leftTop!!.y + movement).toFloat()))) leftTop!![leftTop!!.x + movement] = leftTop!!.y + movement
            }
            TOP -> {
                movement = y - leftTop!!.y
                if (isInImageRange(PointF((leftTop!!.x + movement).toFloat(), (leftTop!!.y + movement).toFloat()))) leftTop!![leftTop!!.x + movement] = leftTop!!.y + movement
            }
            RIGHT -> {
                movement = x - rightBottom!!.x
                if (isInImageRange(PointF((rightBottom!!.x + movement).toFloat(), (rightBottom!!.y + movement).toFloat()))) rightBottom!!.set(rightBottom!!.x + movement, rightBottom!!.y + movement)
            }
            BOTTOM -> {
                movement = y - rightBottom!!.y
                if (isInImageRange(PointF((rightBottom!!.x + movement).toFloat(), (rightBottom!!.y + movement).toFloat()))) rightBottom!!.set(rightBottom!!.x + movement, rightBottom!!.y + movement)
            }
            DRAG -> {
                movement = x - previous!!.x
                val movementY: Int = y - previous!!.y
                if (isInImageRange(PointF((leftTop!!.x + movement).toFloat(), (leftTop!!.y + movementY).toFloat())) && isInImageRange(PointF((rightBottom!!.x + movement).toFloat(), (rightBottom!!.y + movementY).toFloat()))) {
                    leftTop!![leftTop!!.x + movement] = leftTop!!.y + movementY
                    rightBottom!!.set(rightBottom!!.x + movement, rightBottom!!.y + movementY)
                }
            }
        }
    }

    private fun getAffectedSide(x: Float, y: Float): Int {
        val buffer = 10
        return if (x >= leftTop!!.x - buffer && x <= leftTop!!.x + buffer) LEFT else if (y >= leftTop!!.y - buffer && y <= leftTop!!.y + buffer) TOP else if (x >= rightBottom!!.x - buffer && x <= rightBottom!!.x + buffer) RIGHT else if (y >= rightBottom!!.y - buffer && y <= rightBottom!!.y + buffer) BOTTOM else DRAG
    }

    fun getCroppedImage(): ByteArray? {
        val drawable = drawable as BitmapDrawable
        val x: Float = (leftTop!!.x - center!!.x + drawable.bitmap.width / 2).toFloat()
        val y: Float = (leftTop!!.y - center!!.y + drawable.bitmap.height / 2).toFloat()
        val cropped = Bitmap.createBitmap(drawable.bitmap, x.toInt(), y.toInt(), rightBottom!!.x - leftTop!!.x, rightBottom!!.y - leftTop!!.y)
        val stream = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}