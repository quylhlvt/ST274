package com.oc.catemoji.catoc.ui.main.pause

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.lvt.ads.util.Admob
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.base.BaseActivity
import com.oc.catemoji.catoc.core.extention.InternetExtension.isInternetAvailable
import com.oc.catemoji.catoc.core.extention.InternetExtension.isNetworkConnected
import com.oc.catemoji.catoc.core.extention.gone
import com.oc.catemoji.catoc.core.extention.invisible
import com.oc.catemoji.catoc.core.extention.onClick
import com.oc.catemoji.catoc.core.extention.saveToFile
import com.oc.catemoji.catoc.core.extention.setFrameActionBar
import com.oc.catemoji.catoc.core.extention.setImageActionBar
import com.oc.catemoji.catoc.core.extention.setTextActionBar
import com.oc.catemoji.catoc.core.extention.visible
import com.oc.catemoji.catoc.databinding.ActivityPauseOcchallengeBinding
import com.oc.catemoji.catoc.ui.main.customize.CustomizeActivity
import com.oc.catemoji.catoc.ui.main.createPony.ChooseCatActivity
import com.oc.catemoji.catoc.ui.main.done.DoneActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class PauseOCChallengeActivity : BaseActivity<ActivityPauseOcchallengeBinding, PauseChallengeViewModel>(
    ActivityPauseOcchallengeBinding::inflate,
    PauseChallengeViewModel::class.java
) {
    private val layerViews = mutableMapOf<Int, ImageView>()
    private var runner: Job? = null
    private var isPlaying = false
    private var isLoadingFrame = false
    private var isSaving = false
    private var isPausedByUser = false
    private var touchGuideShown = false
    private var hasSelectedFirstImage = false
    private fun showTouchGuide() {
        binding.touch.visibility = View.GONE

        if (!intent.getBooleanExtra("SHOW_TOUCH_GUIDE", false)) {
            return
        }
        // Hiển thị ImageView
        if (!touchGuideShown){
        binding.touch.visibility = View.VISIBLE
        // Load GIF
        Glide.with(this)
            .asGif()
            .load(R.drawable.touch)
            .into(binding.touch)

        // Tự ẩn sau 3 giây
        lifecycleScope.launch {
            delay(3000L)

            binding.touch.visibility = View.GONE

            Glide.with(this@PauseOCChallengeActivity)
                .clear(binding.touch)
        }
        touchGuideShown = true
    }}
    private fun initNativeCollab() {
        Admob.getInstance()
            .loadNativeCollapNotBanner(this@PauseOCChallengeActivity, getString(R.string.native_cl_playPauOC), binding.flNativeCollab)
    }

    override fun onRestart() {
        super.onRestart()
        initNativeCollab()
    }
    override fun initView() {
        initNativeCollab()
        setImageActionBar(binding.actionBar.btnActionBarLeft, R.drawable.back_app)
        setFrameActionBar(binding.actionBar.btnActionBarRightText,binding.actionBar.tvRightText,getString(R.string.next ))
        binding.actionBar.btnActionBarRightText.invisible()
        binding.txtPlayoragain.isSelected = true
        updateControls()
        lifecycleScope.launch {
            viewModelActivity.localDataReady.first { it }
            if (viewModel.session == null) {
                val templates = viewModelActivity.templates.value
                val id = intent.getStringExtra(CustomizeActivity.ARG_TEMPLATE_ID)
                val index = intent.getIntExtra(CustomizeActivity.ARG_TEMPLATE_INDEX, 0)
                val template = if (id != null) templates.firstOrNull { it.id == id }
                else templates.getOrNull(index)
                if (template == null) {
                    showToast(R.string.errorcontent)
                    finish()
                    return@launch
                }
                viewModel.session = PauseChallengeSession(template)
            }
            val session = viewModel.session ?: return@launch
            if (session.stages.isEmpty()) {
                showToast(R.string.errorcontent)
                finish()
                return@launch
            }
            buildLayers()
            if (session.isComplete) binding.rlCharacterPause.doOnLayout { exportResult() }
            else prepareCurrentStage()
        }
    }

    override fun viewListener() {
        binding.actionBar.btnActionBarLeft.onClick { handlePauseBack() }
        binding.actionBar.btnActionBarRightText.onClick { exportResult() }
        binding.btnPlayOrPause.onClick {
            val session = viewModel.session ?: return@onClick
            if (isSaving) return@onClick
            if (session.isComplete) {
                exportResult()
                return@onClick
            }
            if (isPlaying) pausePlayback() else startPlayback()
        }
        binding.btnSkip.onClick {
            if (checkOnlineNetworkOrShowDialog()) return@onClick
            advanceStage(skip = true)
        }
        binding.rlCharacterPause.setOnClickListener { advanceStage() }
        binding.root.setOnClickListener { advanceStage() }
    }

    private fun buildLayers() {
        val session = viewModel.session ?: return
        binding.rlCharacterPause.removeAllViews()
        layerViews.clear()
        session.template.listPath.withIndex().sortedBy { it.value.position }.forEach { (index, _) ->
            val image = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(viewModel.displayedBitmaps[index])
            }
            layerViews[index] = image
            binding.rlCharacterPause.addView(image)
        }
    }

    private fun prepareCurrentStage() {
        val session = viewModel.session ?: return
        val stage = session.currentStage ?: return
        updateControls()
        if (viewModel.displayedBitmaps[stage.partIndex] != null) return
        runner?.cancel()
        runner = lifecycleScope.launch {
            try {
                isLoadingFrame = true
                updateControls()
                session.randomChoice()?.let { displayFrame(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showFrameError(e)
            } finally {
                isLoadingFrame = false
                updateControls()
            }
        }
    }

    private fun startPlayback() {
        if (viewModel.session?.isComplete != false) return

        binding.btnSkip.visibility = View.VISIBLE
        showTouchGuide()

        isPausedByUser = false
        runner?.cancel()
        isPlaying = true
        updateControls()

        runner = lifecycleScope.launch {
            try {
                while (isActive && isPlaying) {
                    val choice = viewModel.session?.randomChoice() ?: break
                    displayFrame(choice)
                    delay(FRAME_DELAY_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isPlaying = false
                showFrameError(e)
            } finally {
                updateControls()
            }
        }
    }

    private fun pausePlayback(byUser: Boolean = true) {
        binding.btnSkip.visibility = View.GONE
        isPausedByUser = byUser
        isPlaying = false
        runner?.cancel()
        updateControls()
    }

    private suspend fun displayFrame(choice: PauseChallengeSession.Choice) {
        val session = viewModel.session ?: return
        val stage = session.currentStage ?: return
        val bitmap = loadFrame(choice.path)
        currentCoroutineContext().ensureActive()
        if (session.currentStage?.partIndex != stage.partIndex) return
        // This runs on Main, together with the touch handler. A pending request never
        // overwrites a layer that the player has already frozen or skipped.
        session.show(choice)
        viewModel.displayedBitmaps[stage.partIndex] = bitmap
        layerViews[stage.partIndex]?.setImageBitmap(bitmap)
        updateControls()
    }

    private suspend fun loadFrame(path: String): Bitmap {
        viewModel.frames.get(path)?.let { return it }
        val manager = Glide.with(applicationContext)
        val target = manager.asBitmap().load(path)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .timeout(10_000)
            .submit(FRAME_SIZE, FRAME_SIZE)
        try {
            val bitmap = runInterruptible(Dispatchers.IO) {
                target.get().copy(Bitmap.Config.ARGB_8888, false)
                    ?: error("Cannot copy challenge frame")
            }
            viewModel.frames.put(path, bitmap)
            return bitmap
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { manager.clear(target) }
        }
    }

    private fun advanceStage(skip: Boolean = false) {
        val session = viewModel.session ?: return
        if (isSaving || session.isComplete || (!skip && !isPlaying)) return
        val stage = session.currentStage ?: return
        if (!session.advance(skip)) return

        if (!skip) {
            hasSelectedFirstImage = true
        }

        runner?.cancel()
        updateControls()
        if (skip) {
            viewModel.displayedBitmaps.remove(stage.partIndex)
            layerViews[stage.partIndex]?.setImageDrawable(null)
        }
        if (session.isComplete) {
            isPlaying = false
            exportResult()
        } else if (isPlaying) {
            startPlayback()
        } else {
            prepareCurrentStage()
        }
    }

    private fun checkOnlineNetworkOrShowDialog(): Boolean {
        val isOnlineTemplate = viewModel.session
            ?.template
            ?.id
            ?.startsWith("online_") == true
        if (!isOnlineTemplate) return false

        val hasNetwork = isInternetAvailable(this) && isNetworkConnected(this)
        if (hasNetwork) return false

        showUnstableNetworkDialog()
        return true
    }

    private fun updateControls() {
        val session = viewModel.session
        val available = session != null && !session.isComplete && !isSaving

        binding.icPlayoragain.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_pause
        )

        binding.txtPlayoragain.setText(
            if (isPlaying) R.string.pause else R.string.play
        )

        binding.btnPlayOrPause.isEnabled = session != null && !isSaving && !isLoadingFrame
        binding.btnSkip.isEnabled = available && !isLoadingFrame

        val showNext = (isPausedByUser || session?.isComplete == true) &&
                !isPlaying &&
                (hasSelectedFirstImage || session?.isComplete == true) &&
                session != null && !isSaving

        binding.actionBar.btnActionBarRightText.visibility =
            if (showNext) View.VISIBLE else View.INVISIBLE
    }

    private fun showFrameError(error: Exception) {
        Log.e("PauseOCChallenge", "Unable to load challenge frame", error)
        showToast(R.string.download_failed_please_try_again_later)
    }

    private fun exportResult() {
        val session = viewModel.session ?: return
        if (isSaving || isFinishing || isDestroyed) return
        val root = binding.rlCharacterPause
        if (root.width == 0 || root.height == 0) {
            root.doOnLayout { exportResult() }
            return
        }
        isSaving = true
        // Freeze the visible result before capturing, including when Next ends a run early.
        pausePlayback()
        updateControls()
        showLoadingSafe()
        lifecycleScope.launch {
            try {
                val path = viewModel.resultPath ?: run {
                    // The current nav is still a preview until advanceStage confirms it.
                    // Exclude it only from the export; Pause keeps the visible preview intact.
                    val previewLayer = session.currentStage?.partIndex?.let(layerViews::get)
                    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    for (index in 0 until root.childCount) {
                        val layer = root.getChildAt(index)
                        if (layer === previewLayer || layer.visibility != View.VISIBLE) continue
                        val saveCount = canvas.save()
                        canvas.translate(layer.left.toFloat(), layer.top.toFloat())
                        layer.draw(canvas)
                        canvas.restoreToCount(saveCount)
                    }
                    val savedPath = withContext(Dispatchers.IO) {
                        bitmap.saveToFile(applicationContext, "pause_${System.nanoTime()}")
                    } ?: error("Cannot save challenge bitmap")
                    viewModelActivity.customizeBitmap = bitmap
                    viewModel.resultPath = savedPath
                    savedPath
                }
                hideLoadingSafe()
                startActivity(Intent(this@PauseOCChallengeActivity, DoneActivity::class.java).apply {
                    putExtra(DoneActivity.EXTRA_IMAGE_PATH, path)
                    putExtra(CustomizeActivity.ARG_TEMPLATE_ID, session.template.id)
                    putExtra(
                        CustomizeActivity.ARG_TEMPLATE_INDEX,
                        intent.getIntExtra(CustomizeActivity.ARG_TEMPLATE_INDEX, 0)
                    )
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PauseOCChallenge", "Unable to export challenge", e)
                hideLoadingSafe()
                showToast(R.string.download_failed_please_try_again_later)
            } finally {
                isSaving = false
                updateControls()
            }
        }
    }

    override fun onScreenStart() {
        updateControls()
    }

    private fun handlePauseBack() {
        if (!intent.getBooleanExtra(EXTRA_RESTART_FROM_DONE, false)) {
            finish()
            return
        }

        // Try Again tạo một Pause mới; xoá Pause cũ và quay về danh mục chọn mèo.
        startActivity(Intent(this, ChooseCatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(ChooseCatActivity.EXTRA_PAUSE_CHALLENGE, true)
        })
        finish()
    }

    override fun onScreenBackPressed(): Boolean {
        handlePauseBack()
        return true
    }

    override fun onScreenStop() {
        // App bị đưa xuống nền cũng được xem là một lần pause của người dùng:
        // khi quay lại, nút Next phải được khôi phục nếu đã có layer được chọn.
        pausePlayback(byUser = true)
    }

    companion object {
        const val EXTRA_RESTART_FROM_DONE = "restart_from_done"
        const val FRAME_SIZE = 768
        const val FRAME_DELAY_MS = 180L
    }
}
