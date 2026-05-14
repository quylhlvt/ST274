package com.oc.catemoji.catoc.core.custom.listener.listenerdraw

import android.view.MotionEvent
import com.oc.catemoji.catoc.core.custom.DrawKey
import com.oc.catemoji.catoc.core.custom.DrawView


class FlipEvent : DrawEvent {
    override fun onActionDown(tattooView: DrawView?, event: MotionEvent?) {}
    override fun onActionMove(tattooView: DrawView?, event: MotionEvent?) {}
    override fun onActionUp(tattooView: DrawView?, event: MotionEvent?) {
        if (tattooView != null && tattooView.getStickerCount() > 0) tattooView.flipCurrentDraw(
            DrawKey.FLIP_HORIZONTALLY)
    }
}