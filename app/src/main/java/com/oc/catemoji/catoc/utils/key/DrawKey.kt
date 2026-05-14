package com.oc.catemoji.catoc.utils.key

object DrawKey {
    const val NONE = 0
    const val DRAG = 1
    const val ZOOM_WITH_TWO_FINGER = 2
    const val ICON = 3
    const val CLICK = 4

    const val FLIP_HORIZONTALLY = 1
    const val FLIP_VERTICALLY = 1 shl 1

    const val CENTER = 1
    const val TOP = 1 shl 1
    const val LEFT = 1 shl 2
    const val RIGHT = 1 shl 3
    const val BOTTOM = 1 shl 4

    const val TOP_LEFT = 0
    const val RIGHT_TOP = 1
    const val LEFT_BOTTOM = 2
    const val RIGHT_BOTTOM = 3

    const val DEFAULT_RADIUS = 35f
    const val SINGLE_CLICK_TIME = 500

    const val ellipsis = "\u2026"
}