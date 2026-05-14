package com.oc.catemoji.catoc.utils.share.whatsapp

import android.content.Context

object LocalStorageUtils {

    private const val BUNDLE_NAME = "APP_BUNDLE_NAME"

    fun readData(context: Context, key: String): Any? {
        val sharedPref = context.getSharedPreferences(BUNDLE_NAME, Context.MODE_PRIVATE)
        return sharedPref.all[key]
    }
}