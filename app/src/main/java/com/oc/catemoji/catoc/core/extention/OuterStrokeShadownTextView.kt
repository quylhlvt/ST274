package com.oc.catemoji.catoc.core.extention

import android.content.Context
import android.graphics.*
import android.graphics.Paint.Join
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.oc.catemoji.catoc.R
import ir.kotlin.kavehcolorpicker.dp

class OuterStrokeShadownTextView : AppCompatTextView {

    private var outerStrokeWidth = 0f
    private var outerStrokeColor: Int = Color.WHITE
    private var outerStrokeJoin: Join = Join.ROUND
    private var strokeMiter = 5f
    private var extraPadding = 0
    private var isDrawingStroke = false

    constructor(context: Context) : super(context) { init(null) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(attrs) }

    private fun init(attrs: AttributeSet?) {
        if (attrs == null) return
        val a = context.obtainStyledAttributes(attrs, R.styleable.OuterStrokeTextView)
        try {
            outerStrokeWidth = a.getDimension(R.styleable.OuterStrokeTextView_outerStrokeWidth, 0f)
            outerStrokeColor = a.getColor(R.styleable.OuterStrokeTextView_outerStrokeColor, Color.WHITE)
            outerStrokeJoin = when (a.getInt(R.styleable.OuterStrokeTextView_outerStrokeJoinStyle, 2)) {
                0 -> Join.MITER
                1 -> Join.BEVEL
                else -> Join.ROUND
            }
        } finally {
            a.recycle()
        }
        if (outerStrokeWidth > 0f) {
            extraPadding = (outerStrokeWidth * dp(2)).toInt()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (extraPadding > 0) {
            setPadding(paddingLeft + extraPadding, paddingTop, paddingRight + extraPadding, paddingBottom)
            extraPadding = 0
        }
        if (outerStrokeWidth > 0f) {
            post { setLayerType(LAYER_TYPE_SOFTWARE, null) }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (outerStrokeWidth <= 0f) {
            super.onDraw(canvas)
            return
        }

        val p = paint
        val originalColors = textColors
        val originalStyle = p.style
        val originalStrokeWidth = p.strokeWidth
        val originalJoin = p.strokeJoin

        // ── Vẽ stroke ──────────────────────────────────────
        isDrawingStroke = true
        super.setTextColor(outerStrokeColor)
        p.clearShadowLayer()
        p.style = Paint.Style.STROKE
        p.strokeWidth = outerStrokeWidth
        p.strokeJoin = outerStrokeJoin
        p.strokeMiter = strokeMiter
        p.isAntiAlias = true
        super.onDraw(canvas)

        // ── Vẽ fill + shadow ────────────────────────────────
        isDrawingStroke = false
        super.setTextColor(originalColors)
        p.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor)
        p.style = originalStyle
        p.strokeWidth = originalStrokeWidth
        p.strokeJoin = originalJoin
        super.onDraw(canvas)
    }

    override fun invalidate() {
        if (isDrawingStroke) return          // ✅ chặn loop
        super.invalidate()
    }

    override fun postInvalidate() {
        if (isDrawingStroke) return          // ✅ chặn thêm postInvalidate
        super.postInvalidate()
    }
}