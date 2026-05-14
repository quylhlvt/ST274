package com.oc.catemoji.catoc.core.extention

import android.graphics.RectF
import kotlin.math.roundToInt

fun toRect(r: RectF, array: FloatArray) {
    r[Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY] =
        Float.NEGATIVE_INFINITY
    var i = 1
    while (i < array.size) {
        val x = (array[i - 1] * 10).roundToInt() / 10f
        val y = (array[i] * 10).roundToInt() / 10f
        r.left = if (x < r.left) x else r.left
        r.top = if (y < r.top) y else r.top
        r.right = if (x > r.right) x else r.right
        r.bottom = if (y > r.bottom) y else r.bottom
        i += 2
    }
    r.sort()
}