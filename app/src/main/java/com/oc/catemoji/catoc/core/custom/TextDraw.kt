package com.oc.catemoji.catoc.core.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.ColorInt
import androidx.annotation.IntRange

class TextDraw @JvmOverloads constructor(private val context: Context, drawable: Drawable? = null, filePath: String? = null) : Draw() {

    override val width: Int
        get() = drawable.intrinsicWidth
    override val height: Int
        get() = drawable.intrinsicHeight

    private val bounds: Rect
    private val textRect: Rect
    private val textPaint: TextPaint
    private var _drawable: Drawable? = null
    override var drawable: Drawable
        get() {
            return _drawable ?: drawable
        }
        set(value) {
            _drawable = value
        }
    private var staticLayout: StaticLayout? = null
    var textAlign: Layout.Alignment
        private set
    var text: String? = null
        private set
    var typeface: Typeface? = null
        private set
    var textColor1 = 0
        private set
    var idTypeFace = 0
    var textCheckAlign: String? = null
    override val drawablePath: String

    private var maxTextSizePixels: Float
    var minTextSizePixels: Float
        private set
    private var lineSpacingMultiplier = 1.0f
    private var lineSpacingExtra = 0.0f

    init {
        drawablePath = filePath!!
        this.drawable = drawable!!
        textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        bounds = Rect(0, 0, width, height)
        textRect = Rect(0, 0, width, height)
        minTextSizePixels = convertSpToPx(6f)
        maxTextSizePixels = convertSpToPx(20f)
        textAlign = Layout.Alignment.ALIGN_CENTER
        textPaint.textSize = maxTextSizePixels
    }

    override fun draw(canvas: Canvas) {
        val matrix = getMatrix()
        canvas.save()
        canvas.concat(matrix)
        if (drawable != null) {
            drawable.bounds = bounds
            drawable.draw(canvas)
        }
        canvas.restore()
        canvas.save()
        canvas.concat(matrix)
        if (textRect.width() == width) {
            val dy = height / 2 - staticLayout!!.height / 2
            canvas.translate(0f, dy.toFloat())
        } else {
            val dx = textRect.left
            val dy = textRect.top + textRect.height() / 2 - staticLayout!!.height / 2
            canvas.translate(dx.toFloat(), dy.toFloat())
        }
        staticLayout!!.draw(canvas)
        canvas.restore()
    }

    override fun release() {
        super.release()
    }

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int): TextDraw {
        textPaint.alpha = alpha
        return this
    }

    override fun setDrawable(drawable: Drawable): TextDraw {
        this.drawable = drawable
        bounds[0, 0, width] = height
        textRect[0, 0, width] = height
        return this
    }

    fun setTextColor(@ColorInt color: Int): TextDraw {
        textColor = color
        textPaint.color = color
        return this
    }

    fun resizeText(): TextDraw {
        val heightPixels = textRect.height()
        val widthPixels = textRect.width()
        val text: CharSequence? = text

        if (text.isNullOrEmpty() || heightPixels <= 0 || widthPixels <= 0 || maxTextSizePixels <= 0) {
            return this
        }
        var targetTextSizePixels = maxTextSizePixels
        var targetTextHeightPixels = getTextHeightPixels(text, widthPixels, targetTextSizePixels)

        while (targetTextHeightPixels > heightPixels && targetTextSizePixels > minTextSizePixels) {
            targetTextSizePixels = (targetTextSizePixels - 2).coerceAtLeast(minTextSizePixels)
            targetTextHeightPixels = getTextHeightPixels(text, widthPixels, targetTextSizePixels)
        }

        if (targetTextSizePixels == minTextSizePixels && targetTextHeightPixels > heightPixels) {
            val textPaintCopy = TextPaint(textPaint)
            textPaintCopy.textSize = targetTextSizePixels

            val staticLayout = StaticLayout(text, textPaintCopy, widthPixels, Layout.Alignment.ALIGN_NORMAL, lineSpacingMultiplier, lineSpacingExtra, false)

            if (staticLayout.lineCount > 0) {
                val lastLine = staticLayout.getLineForVertical(heightPixels) - 1
                if (lastLine >= 0) {
                    val startOffset = staticLayout.getLineStart(lastLine)
                    var endOffset = staticLayout.getLineEnd(lastLine)
                    var lineWidthPixels = staticLayout.getLineWidth(lastLine)
                    val ellipseWidth = textPaintCopy.measureText(DrawKey.ellipsis)

                    while (widthPixels < lineWidthPixels + ellipseWidth) {
                        endOffset--
                        lineWidthPixels = textPaintCopy.measureText(text.subSequence(startOffset, endOffset + 1).toString())
                    }
                    setText(text.subSequence(0, endOffset).toString() + DrawKey.ellipsis)
                }
            }
        }
        textPaint.textSize = targetTextSizePixels
        staticLayout = StaticLayout(this.text, textPaint, textRect.width(), textAlign, lineSpacingMultiplier, lineSpacingExtra, true)
        return this
    }

    fun setTypeface(typeface: Typeface?): TextDraw {
        this.typeface = typeface
        textPaint.setTypeface(typeface)
        return this
    }

    fun setTextAlign(alignment: Layout.Alignment): TextDraw {
        textAlign = alignment
        return this
    }

    fun setText(text: String?): TextDraw {
        this.text = text
        return this
    }

    private fun getTextHeightPixels(source: CharSequence, availableWidthPixels: Int, textSizePixels: Float): Int {
        textPaint.textSize = textSizePixels
        val staticLayout = StaticLayout(source, textPaint, availableWidthPixels, Layout.Alignment.ALIGN_NORMAL, lineSpacingMultiplier, lineSpacingExtra, true)
        return staticLayout.height
    }

    private fun convertSpToPx(scaledPixels: Float): Float {
        return scaledPixels * context.resources.displayMetrics.scaledDensity
    }

}
