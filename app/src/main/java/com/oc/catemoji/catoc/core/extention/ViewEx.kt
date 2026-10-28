package com.oc.catemoji.catoc.core.extention

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView

fun Context.strings(resId: Int): String {
    return getString(resId)
}

fun setImageActionBar(imageView: ImageView, res: Int) {
    imageView.setImageResource(res)
    imageView.visible()
}
fun setTextActionBar(textView: TextView, text: String) {
    textView.text = text
    textView.visible()
    textView.isSelected = true
}


fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.select() {
    isSelected = true
}

private val lastClickTime = java.util.concurrent.atomic.AtomicLong(0L)

fun View.onClick(interval: Long = 200, action: (View) -> Unit) {
    setOnClickListener {
        val now = System.currentTimeMillis()
        if (lastClickTime.getAndSet(now).let { now - it >= interval }) {
            action(it)
        }
    }
}
