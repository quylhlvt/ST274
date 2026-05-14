package com.oc.catemoji.catoc.data.model.custom

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class SelectedModel(
    val color: Int = 0,
    val isSelected: Boolean = false
): Parcelable
