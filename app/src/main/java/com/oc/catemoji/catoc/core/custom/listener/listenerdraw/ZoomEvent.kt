package com.oc.catemoji.catoc.core.custom.listener.listenerdraw

import android.view.MotionEvent
import com.oc.catemoji.catoc.core.custom.DrawView

class ZoomEvent : DrawEvent {
    override fun onActionDown(drawView: DrawView?, event: MotionEvent?) {}
    override fun onActionMove(drawView: DrawView?, event: MotionEvent?) {
        drawView!!.rotateZoomCurrentDraw(event!!)
    }
    override fun onActionUp(drawView: DrawView?, event: MotionEvent?) {
        if (drawView!!.getOnDrawListener() != null) {
            drawView.getOnDrawListener()!!.onZoomFinishedDraw(drawView.getCurrentDraw()!!)
        }
    }
}
