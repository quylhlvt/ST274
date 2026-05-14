package com.oc.catemoji.catoc.utils.share.whatsapp

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.viewbinding.ViewBinding
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseFragment

abstract class WhatsappSharingFragment<VB : ViewBinding, VM : ViewModel>(
    bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> VB,
    viewModelClass: Class<VM>
) : BaseFragment<VB, VM>(bindingInflater, viewModelClass){
    companion object {
        private const val ADD_PACK_REQUEST = 200
        private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
        private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
        private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"
        private const val MIN_STICKERS_REQUIRED = 3
    }

    fun addToWhatsapp(sp: StickerPack) {
        if (sp.stickers.size >= MIN_STICKERS_REQUIRED) {
            addStickerPackageToWhatsApp(sp)
        } else {
            showErrorDialog()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ADD_PACK_REQUEST) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.d("WhatsappSharing", "Sticker pack added successfully")
                }
                Activity.RESULT_CANCELED -> {
                    val validationError = data?.getStringExtra("validation_error")
                    if (validationError != null) {
                        Log.e("WhatsappSharing", "Validation failed: $validationError")
                        showToast("Failed: $validationError")
                    } else {
                        Log.d("WhatsappSharing", "User cancelled the action")
                    }
                }
                else -> {
                    Log.d("WhatsappSharing", "Add StickerPack to WhatsApp request received: $resultCode")
                }
            }
        }
    }

    private fun addStickerPackageToWhatsApp(sp: StickerPack) {
        val intent = Intent().apply {
            action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
            putExtra(EXTRA_STICKER_PACK_ID, sp.identifier)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, WhitelistCheck.CONTENT_PROVIDER_AUTHORITY)
            putExtra(EXTRA_STICKER_PACK_NAME, sp.name)
        }

        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, ADD_PACK_REQUEST)
        } catch (e: ActivityNotFoundException) {
            showToast(R.string.invalid_action_msg)
            Log.e("WhatsappSharing", "WhatsApp not installed", e)
        } catch (e: Exception) {
            showToast("Failed to add sticker pack")
            Log.e("WhatsappSharing", "Error adding sticker pack", e)
        }
    }

    private fun showErrorDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.invalid_action)
            .setMessage(R.string.invalid_action_msg)
            .setNegativeButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}