package com.oc.catemoji.catoc.data.audio

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Foreground host for instrumentation; never captures or stores audio itself. */
class AudioTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
