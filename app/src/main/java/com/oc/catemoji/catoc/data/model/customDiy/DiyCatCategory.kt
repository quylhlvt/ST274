package com.oc.catemoji.catoc.data.model.customDiy

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize

data class DiyCatCategory(val title: String, val paths: List<String>) : Parcelable

@Parcelize
data class DiyCatalogue(
    val cats: List<DiyCatCategory> = emptyList(),
    val eyes: List<String> = emptyList(),
    val keyboardRows: List<List<String>> = emptyList(),
    val tags: List<String> = emptyList(),
    val selectedCatCategory: Int = 0
) : Parcelable
