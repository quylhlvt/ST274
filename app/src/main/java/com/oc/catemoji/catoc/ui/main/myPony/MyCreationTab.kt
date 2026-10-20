package com.oc.catemoji.catoc.ui.main.myPony

import androidx.annotation.StringRes
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ui.main.add_character.DesignSource

enum class MyCreationTab(@StringRes val titleRes: Int, val designSource: String? = null) {
    AVATAR(R.string.my_avatar),
    DESIGN(R.string.design, DesignSource.CUSTOM),
    // TODO: Restore DesignSource.STUDIO after testing the DIY popup with My Design data.
    DIY(R.string.my_diy, DesignSource.CUSTOM)
}
