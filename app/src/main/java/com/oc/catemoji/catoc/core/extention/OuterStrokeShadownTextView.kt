package com.oc.catemoji.catoc.core.extention

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Join
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.oc.catemoji.catoc.R
import kotlin.math.ceil

class OuterStrokeShadownTextView : AppCompatTextView {

    private var outerStrokeWidth = 0f
    private var outerStrokeColor: Int = Color.WHITE
    private var outerStrokeJoin: Join = Join.ROUND
    private var strokeMiter = 3f
    private var isDrawingStroke = false

    constructor(context: Context) : super(context) { init(null) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) { init(attrs) }

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
            // Nét viền tràn ra ngoài mép chữ một nửa độ dày, ở cả 4 phía.
            val extra = ceil(outerStrokeWidth / 2f).toInt()
            setPadding(
                paddingLeft + extra, paddingTop + extra,
                paddingRight + extra, paddingBottom + extra
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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
        val originalMiter = p.strokeMiter

        // Lưu shadow của người dùng trước khi đổi.
        val sr = shadowRadius
        val sdx = shadowDx
        val sdy = shadowDy
        val sc = shadowColor

        // Chặn invalidate trong suốt onDraw (setTextColor/setShadowLayer đều gọi invalidate).
        isDrawingStroke = true
        try {
            // ── Lượt 1: vẽ viền ─────────────────────────────────────
            super.setTextColor(outerStrokeColor)
            // Shadow trong suốt chỉ để TextView nới vùng cắt ra thêm nửa nét viền.
            super.setShadowLayer(maxOf(sr, outerStrokeWidth / 2f), 0f, 0f, Color.TRANSPARENT)
            p.style = Paint.Style.STROKE
            p.strokeWidth = outerStrokeWidth
            p.strokeJoin = outerStrokeJoin
            p.strokeMiter = strokeMiter
            p.isAntiAlias = true
            super.onDraw(canvas)

            // ── Lượt 2: vẽ chữ + shadow thật ────────────────────────
            super.setTextColor(originalColors)
            super.setShadowLayer(sr, sdx, sdy, sc)
            p.style = originalStyle
            p.strokeWidth = originalStrokeWidth
            p.strokeJoin = originalJoin
            p.strokeMiter = originalMiter
            super.onDraw(canvas)
        } finally {
            isDrawingStroke = false
        }
    }

    override fun invalidate() {
        if (isDrawingStroke) return
        super.invalidate()
    }

    override fun postInvalidate() {
        if (isDrawingStroke) return
        super.postInvalidate()
    }
}