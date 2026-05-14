package com.oc.catemoji.catoc.core.extention

// SelectionExtension.kt
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oc.catemoji.catoc.data.model.custom.SelectionIndex

fun List<*>.toCleanSelections(): ArrayList<SelectionIndex> {
    val gson = Gson()
    val json = gson.toJson(this)
    val type = object : TypeToken<ArrayList<SelectionIndex>>() {}.type
    return gson.fromJson(json, type)
}