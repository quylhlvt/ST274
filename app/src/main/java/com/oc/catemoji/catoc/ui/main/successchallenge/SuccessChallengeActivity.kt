package com.oc.catemoji.catoc.ui.main.successchallenge

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.onClick2
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.helper.DownloadHelper
import com.oc.catemoji.catoc.databinding.ActivitySuccessChallengeBinding
import com.oc.catemoji.catoc.ui.main.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SuccessChallengeActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySuccessChallengeBinding
    private var isDownloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySuccessChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
            ?.takeIf { File(it).isFile }
        if (imagePath == null) {
            Toast.makeText(this, R.string.errorcontent, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setImageActionBar(binding.actionBar.btnActionBarLeft, R.drawable.back_app)
        setTextActionBar(binding.actionBar.tvCenter, getString(R.string.successful))
        setImageActionBar(binding.actionBar.btnActionBarNextToRight, R.drawable.ic_share)
        setImageActionBar(binding.actionBar.btnActionBarRight, R.drawable.ic_home)
        Glide.with(this).load(File(imagePath)).into(binding.imvImage)

        binding.actionBar.btnActionBarLeft.onClick { finish() }
        binding.btnBottomRight.onClick { downloadImage(imagePath) }
        binding.actionBar.btnActionBarNextToRight.onClick2 {
            shareImage()
        }
        binding.actionBar.btnActionBarRight.onClick2 {
            home()
        }
    }

    private fun downloadImage(path: String) {
        if (isDownloading) return
        isDownloading = true
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                DownloadHelper.downloadToGallery(this@SuccessChallengeActivity, path)
            }
            isDownloading = false
            Toast.makeText(
                this@SuccessChallengeActivity,
                if (success) getString(R.string.download_success, getString(R.string.app_name))
                else getString(R.string.download_failed_please_try_again_later),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareImage() {
        val path = intent.getStringExtra(EXTRA_IMAGE_PATH).orEmpty()
        val file = File(path)
        if (!file.isFile) {
            Toast.makeText(this, R.string.errorcontent, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("challenge_image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_found_to_handle_this_action, Toast.LENGTH_SHORT).show()
        }
    }

    private fun home() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "imagePath"
    }
}
