package com.oc.catemoji.catoc.core.helper

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.TypefaceSpan

class CustomTypefaceSpan(private val family: String, private val typeface: Typeface?) :
    TypefaceSpan(family) {

    override fun updateDrawState(ds: TextPaint) {
        applyCustomTypeFace(ds, typeface)
    }

    override fun updateMeasureState(paint: TextPaint) {
        applyCustomTypeFace(paint, typeface)
    }

    private fun applyCustomTypeFace(paint: Paint, tf: Typeface?) {
        if (tf != null) {
            paint.typeface = tf
        } else {
            paint.typeface = Typeface.DEFAULT
        }
    }
}