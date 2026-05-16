package com.oc.catemoji.catoc.utils.share.telegram

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.oc.catemoji.catoc.R

object TelegramSharing {
    fun importToTelegram(context: Context, uriList: List<Uri>) {
        if (uriList.isEmpty()) return

        val list = ArrayList(uriList)

        // Grant permission cho cả Telegram và Telegram X
        val telegramPackages = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.plus"
        )

        list.forEach { uri ->
            telegramPackages.forEach { pkg ->
                try {
                    context.grantUriPermission(
                        pkg,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) { }
            }
        }

        val intent = Intent("org.telegram.messenger.CREATE_STICKER_PACK").apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
            putExtra("IMPORTER", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            type = "image/png" // Telegram yêu cầu PNG cho sticker
        }

        // Tìm đúng package Telegram đang cài
        val resolvedPackage = telegramPackages.firstOrNull { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }

        if (resolvedPackage != null) {
            intent.setPackage(resolvedPackage)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.no_app_found_to_handle_this_action),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}