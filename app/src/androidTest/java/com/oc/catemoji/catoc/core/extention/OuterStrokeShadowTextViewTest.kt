package com.oc.catemoji.catoc.core.extention

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import android.util.Xml
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class OuterStrokeShadowTextViewTest {
    @Test
    fun gradientRunsDownTheTextWhileOutlineAndPaintStateStayIntact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(
                instrumentation.targetContext, androidx.appcompat.R.style.Theme_AppCompat,
            )
            val view = context.resources.getLayout(R.layout.activity_home).use { parser ->
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG &&
                        parser.name == OuterStrokeShadowTextView::class.java.name &&
                        parser.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "id", 0) == R.id.txtCoinBone
                    ) {
                        break
                    }
                    parser.next()
                }
                check(parser.eventType != XmlPullParser.END_DOCUMENT)
                OuterStrokeShadowTextView(context, Xml.asAttributeSet(parser))
            }
            view.text = "9999"
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, 80f)
            // This view is rendered without a window/pre-draw scroll adjustment.
            view.setHorizontallyScrolling(false)
            view.setBackgroundColor(Color.TRANSPARENT)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val originalColors = view.textColors
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                // Fully opaque light pixels belong to the fill, away from anti-aliased edges.
                val fill = pixels.indices.filter { index ->
                    val color = pixels[index]
                    Color.alpha(color) == 255 && Color.red(color) == 255 && Color.green(color) >= 235
                }
                assertTrue(
                    "Gradient fill must be visible; text=${view.text}, colors=" +
                        pixels.toList().groupingBy { it }.eachCount().entries
                            .sortedByDescending { it.value }.take(8)
                            .joinToString { "${Integer.toHexString(it.key)}:${it.value}" },
                    fill.isNotEmpty(),
                )
                val top = fill.minOf { it / bitmap.width }
                val bottom = fill.maxOf { it / bitmap.width }
                val middle = (top + bottom) / 2
                val upperBlue = fill.filter { it / bitmap.width < middle }
                    .map { Color.blue(pixels[it]) }.average()
                val lowerBlue = fill.filter { it / bitmap.width > middle }
                    .map { Color.blue(pixels[it]) }.average()
                assertTrue("Fill must become cream towards the bottom", upperBlue > lowerBlue + 5)
                val topBlue = fill.filter { it / bitmap.width <= top + 2 }
                    .map { Color.blue(pixels[it]) }.average()
                val bottomBlue = fill.filter { it / bitmap.width >= bottom - 2 }
                    .map { Color.blue(pixels[it]) }.average()
                assertTrue("Top of the glyph must reach white", topBlue >= 245)
                assertTrue("Bottom of the glyph must reach cream", bottomBlue <= 214)
                assertTrue("Outline must remain solid black", pixels.any { it == Color.BLACK })
                assertNull(view.paint.shader)
                assertEquals(originalColors, view.textColors)
                bitmap.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(bitmap))
                val second = IntArray(pixels.size)
                bitmap.getPixels(second, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("Repeated draws must not change colors", pixels.contentEquals(second))
            } finally {
                bitmap.recycle()
            }
        }
    }
}
