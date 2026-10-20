package com.oc.catemoji.catoc.ui.main.myPony

import androidx.annotation.StringRes
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.ui.main.add_character.DesignSource

enum class MyCreationTab(@StringRes val titleRes: Int, val designSource: String? = null) {
    AVATAR(R.string.my_avatar),
    DESIGN(R.string.design, DesignSource.CUSTOM),
    DIY(R.string.my_diy, DesignSource.DIY)
}
