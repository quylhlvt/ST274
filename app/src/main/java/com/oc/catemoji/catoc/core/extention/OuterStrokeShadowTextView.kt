package com.oc.catemoji.catoc.core.extention

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.text.Layout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import com.oc.catemoji.catoc.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Plain title text with a solid extrusion behind the entire white outline.
 * Marquee uses TextView's drawing transforms for every layer to keep them aligned.
 * outerStrokeWidth is the visible outline thickness, in pixels after XML conversion.
 */
class OuterStrokeShadowTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val savedPaint = TextPaint()
    private val outlineWidth: Float
    private val outlineColor: Int
    private val outlineJoin: Paint.Join
    private val extrusionColor: Int
    private val extrusionDx: Float
    private val extrusionDy: Float
    private val extrusionSteps: Int
    private val outlineShader: Shader
    private val extrusionShader: Shader
    private val useGradient: Boolean
    private val gradientStartColor: Int
    private val gradientEndColor: Int
    private var fillGradient: LinearGradient? = null
    private var gradientTop = Float.NaN
    private var gradientBottom = Float.NaN
    private val gradientTextBounds = Rect()
    private var gradientLayout: Layout? = null
    private var gradientDirty = true
    private var effectLeft = 0
    private var effectTop = 0
    private var effectRight = 0
    private var effectBottom = 0

    init {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.OuterStrokeShadowTextView, defStyleAttr, 0,
        )
        try {
            outlineWidth = a.getDimension(
                R.styleable.OuterStrokeShadowTextView_outerStrokeWidth,
                resources.getDimension(R.dimen.title_outline_width),
            ).coerceAtLeast(0f)
            outlineColor = a.getColor(
                R.styleable.OuterStrokeShadowTextView_outerStrokeColor,
                context.getColor(R.color.white),
            )
            outlineJoin = when (a.getInt(
                R.styleable.OuterStrokeShadowTextView_outerStrokeJoinStyle, 2,
            )) {
                0 -> Paint.Join.MITER
                1 -> Paint.Join.BEVEL
                else -> Paint.Join.ROUND
            }
            extrusionColor = a.getColor(
                R.styleable.OuterStrokeShadowTextView_extrusionColor,
                context.getColor(R.color.app_color2),
            )
            extrusionDx = a.getDimension(R.styleable.OuterStrokeShadowTextView_extrusionDx, 0f)
            extrusionDy = a.getDimension(
                R.styleable.OuterStrokeShadowTextView_extrusionDy,
                resources.getDimension(R.dimen.dimension_1),
            )
            useGradient = a.getBoolean(
                R.styleable.OuterStrokeShadowTextView_useGradient,
                a.hasValue(R.styleable.OuterStrokeShadowTextView_gradientStartColor) &&
                    a.hasValue(R.styleable.OuterStrokeShadowTextView_gradientEndColor),
            )
            gradientStartColor = a.getColor(
                R.styleable.OuterStrokeShadowTextView_gradientStartColor, currentTextColor,
            )
            gradientEndColor = a.getColor(
                R.styleable.OuterStrokeShadowTextView_gradientEndColor, currentTextColor,
            )
        } finally {
            a.recycle()
        }
        extrusionSteps = ceil(max(abs(extrusionDx), abs(extrusionDy))).toInt()
        // TextView sets the paint color in onDraw; solid shaders retain effect colors.
        outlineShader = LinearGradient(0f, 0f, 0f, 1f, outlineColor, outlineColor, Shader.TileMode.CLAMP)
        extrusionShader = LinearGradient(0f, 0f, 0f, 1f, extrusionColor, extrusionColor, Shader.TileMode.CLAMP)
        // Miter joins can extend farther than the nominal outline thickness.
        val extent = outlineWidth * if (outlineJoin == Paint.Join.MITER) 4f else 1f
        effectLeft = ceil(extent + max(0f, -extrusionDx)).toInt()
        effectTop = ceil(extent + max(0f, -extrusionDy)).toInt()
        effectRight = ceil(extent + max(0f, extrusionDx)).toInt()
        effectBottom = ceil(extent + max(0f, extrusionDy)).toInt()
    }

    // Compound padding participates in both TextView measurement and text positioning.
    override fun getCompoundPaddingLeft() = super.getCompoundPaddingLeft() + effectLeft
    override fun getCompoundPaddingTop() = super.getCompoundPaddingTop() + effectTop
    override fun getCompoundPaddingRight() = super.getCompoundPaddingRight() + effectRight
    override fun getCompoundPaddingBottom() = super.getCompoundPaddingBottom() + effectBottom

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        gradientDirty = true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        gradientDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout
        if (textLayout == null || text.isNullOrEmpty()) {
            super.onDraw(canvas)
            return
        }

        if (ellipsize == TextUtils.TruncateAt.MARQUEE) {
            drawMarquee(canvas, textLayout)
            return
        }

        val availableHeight = height - extendedPaddingTop - extendedPaddingBottom
        val remainingHeight = (availableHeight - textLayout.height).coerceAtLeast(0)
        val verticalOffset = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.BOTTOM -> remainingHeight
            Gravity.CENTER_VERTICAL -> remainingHeight / 2
            else -> 0
        }
        val checkpoint = canvas.save()
        savedPaint.set(paint)
        try {
            canvas.translate(compoundPaddingLeft.toFloat(), (extendedPaddingTop + verticalOffset).toFloat())
            paint.clearShadowLayer()
            paint.shader = null
            paint.isAntiAlias = true
            paint.strokeJoin = outlineJoin
            paint.strokeMiter = 4f
            // A centered stroke loses its inner half under the fill.
            paint.strokeWidth = outlineWidth * 2f
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.color = extrusionColor
            for (step in extrusionSteps downTo 1) {
                val fraction = step.toFloat() / extrusionSteps
                val shadowCheckpoint = canvas.save()
                try {
                    canvas.translate(extrusionDx * fraction, extrusionDy * fraction)
                    textLayout.draw(canvas)
                } finally {
                    canvas.restoreToCount(shadowCheckpoint)
                }
            }
            if (outlineWidth > 0f) {
                paint.color = outlineColor
                textLayout.draw(canvas)
            }
        } finally {
            paint.set(savedPaint)
            canvas.restoreToCount(checkpoint)
        }
        // TextView draws the original fill and compound drawables once. No color
        // setters or invalidation suppression are needed during drawing.
        if (useGradient) {
            updateFillGradient(textLayout)
            val originalShader = paint.shader
            try {
                paint.shader = fillGradient
                super.onDraw(canvas)
            } finally {
                paint.shader = originalShader
            }
        } else {
            super.onDraw(canvas)
        }
    }

    private fun drawMarquee(canvas: Canvas, textLayout: Layout) {
        savedPaint.set(paint)
        try {
            paint.clearShadowLayer()
            paint.isAntiAlias = true
            paint.strokeJoin = outlineJoin
            paint.strokeMiter = 4f
            paint.strokeWidth = outlineWidth * 2f
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.shader = extrusionShader
            for (step in extrusionSteps downTo 1) {
                val fraction = step.toFloat() / extrusionSteps
                val checkpoint = canvas.save()
                try {
                    canvas.translate(extrusionDx * fraction, extrusionDy * fraction)
                    super.onDraw(canvas)
                } finally {
                    canvas.restoreToCount(checkpoint)
                }
            }
            if (outlineWidth > 0f) {
                paint.shader = outlineShader
                super.onDraw(canvas)
            }
            paint.set(savedPaint)
            if (useGradient) {
                updateFillGradient(textLayout)
                paint.shader = fillGradient
            }
            super.onDraw(canvas)
        } finally {
            paint.set(savedPaint)
        }
    }

    private fun updateFillGradient(textLayout: Layout) {
        if (!gradientDirty && gradientLayout === textLayout && fillGradient != null) return
        // Use the actual glyph bounds, rather than the font's empty ascent/descent
        // space, so digits reach both endpoint colors within their visible fill.
        val displayedText = textLayout.text.toString()
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (line in 0 until textLayout.lineCount) {
            val start = textLayout.getLineStart(line)
            val end = textLayout.getLineVisibleEnd(line)
            if (start == end) continue
            paint.getTextBounds(displayedText, start, end, gradientTextBounds)
            if (gradientTextBounds.isEmpty) continue
            val baseline = textLayout.getLineBaseline(line)
            top = minOf(top, baseline + gradientTextBounds.top.toFloat())
            bottom = maxOf(bottom, baseline + gradientTextBounds.bottom.toFloat())
        }
        if (!top.isFinite() || !bottom.isFinite()) {
            top = textLayout.getLineBaseline(0) + paint.ascent()
            bottom = textLayout.getLineBaseline(textLayout.lineCount - 1) + paint.descent()
        }
        bottom = bottom.coerceAtLeast(top + 1f)
        gradientLayout = textLayout
        gradientDirty = false
        if (fillGradient == null || top != gradientTop || bottom != gradientBottom) {
            gradientTop = top
            gradientBottom = bottom
            fillGradient = LinearGradient(
                0f, top, 0f, bottom,
                gradientStartColor, gradientEndColor, Shader.TileMode.CLAMP,
            )
        }
    }
}
