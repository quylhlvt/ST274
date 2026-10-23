package com.oc.catemoji.catoc.ui.main.pause

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class PauseChallengeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class LoadedFrame(
        val choice: PauseChallengeSession.Choice,
        val bitmap: Bitmap
    )

    private data class PendingFrame(
        val stageIndex: Int,
        val choice: PauseChallengeSession.Choice,
        val bitmap: Deferred<Bitmap>
    )

    var session: PauseChallengeSession? = null
        set(value) {
            if (field !== value) cancelPrefetchedFrames()
            field = value
        }
    val displayedBitmaps = mutableMapOf<Int, Bitmap>()
    var previewPartIndex: Int? = null
    var previewBitmap: Bitmap? = null
    var resultPath: String? = null

    private val frameBuffer = ArrayDeque<PendingFrame>()
    private val pendingFramePaths = mutableSetOf<String>()
    private val slowFramePaths = mutableSetOf<String>()
    private var bufferedStageIndex = NO_STAGE
    private var nextStageFrame: PendingFrame? = null

    private val isLowRamDevice by lazy {
        context.getSystemService(ActivityManager::class.java).isLowRamDevice
    }
    private val frameBufferCapacity by lazy {
        if (isLowRamDevice) LOW_RAM_FRAME_BUFFER_SIZE else FRAME_BUFFER_SIZE
    }
    private val frameSize by lazy {
        val sizeCap = if (isLowRamDevice) LOW_RAM_FRAME_SIZE else FRAME_SIZE
        minOf(
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
            sizeCap
        ).coerceAtLeast(1)
    }

    // Cached frames own their pixels; releasing a Glide request cannot change a frozen layer.
    private val frames = object : LruCache<String, Bitmap>(frameCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun awaitNextFrame(): LoadedFrame? {
        val activeSession = session ?: return null
        var failures = 0
        var lastError: Exception? = null

        // Dừng hẳn sau MAX_LOAD_FAILURES lần lỗi liên tiếp, dọn buffer để lần Play sau tải lại.
        fun registerFailure(error: Exception?) {
            if (error != null) lastError = error
            if (++failures >= MAX_LOAD_FAILURES) {
                cancelPrefetchedFrames()
                throw lastError ?: IllegalStateException("Cannot load challenge frames")
            }
        }

        while (true) {
            val stageIndex = activeSession.stageIndex
            repeat(dropFailedFrames()) { registerFailure(null) }
            prefetchFrames()

            val candidates = frameBuffer.filter { it.stageIndex == stageIndex }
            if (candidates.isEmpty()) {
                lastError?.let { throw it }
                return null
            }

            val pending = candidates.firstOrNull { it.bitmap.isCompleted }
                ?: withTimeoutOrNull(SLOW_FRAME_SWITCH_MS) {
                    awaitFirstCompletedFrame(candidates)
                }

            if (pending == null) {
                val slowFrame = frameBuffer.peekFirst()
                if (slowFrame != null && hasUnqueuedAlternative(slowFrame.choice.path)) {
                    removePendingFrame(slowFrame)
                    slowFramePaths += slowFrame.choice.path
                    slowFrame.bitmap.cancel()
                    continue
                }

                val completed = awaitFirstCompletedFrame(candidates)
                removePendingFrame(completed)
                try {
                    return LoadedFrame(completed.choice, completed.bitmap.await())
                } catch (cancelled: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    slowFramePaths += completed.choice.path
                    registerFailure(null)
                    continue
                } catch (error: Exception) {
                    slowFramePaths += completed.choice.path
                    registerFailure(error)
                    continue
                }
            }

            removePendingFrame(pending)
            if (pending.stageIndex != stageIndex) {
                pending.bitmap.cancel()
                continue
            }
            try {
                return LoadedFrame(pending.choice, pending.bitmap.await())
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                slowFramePaths += pending.choice.path
                registerFailure(null)
            } catch (error: Exception) {
                slowFramePaths += pending.choice.path
                registerFailure(error)
            }
        }
    }

    fun prefetchFrames() {
        val activeSession = session ?: return
        if (activeSession.isComplete) return

        val stageIndex = activeSession.stageIndex
        if (bufferedStageIndex != stageIndex) {
            clearCurrentFrameBuffer()
            bufferedStageIndex = stageIndex
        }

        while (frameBuffer.size < frameBufferCapacity) {
            val choice = nextBufferedChoice() ?: break
            val priority = if (frameBuffer.isEmpty()) Priority.IMMEDIATE else Priority.HIGH
            pendingFramePaths += choice.path
            frameBuffer.addLast(
                PendingFrame(
                    stageIndex = stageIndex,
                    choice = choice,
                    bitmap = viewModelScope.async { loadFrame(choice.path, priority) }
                )
            )
        }

        // The visible stage gets the first IMMEDIATE request. Only then prefetch the
        // next navigation so background work cannot delay the image needed now.
        prefetchNextStageFrame(activeSession, stageIndex)
    }

    fun promoteNextStageFrame(stageIndex: Int) {
        clearCurrentFrameBuffer()
        bufferedStageIndex = stageIndex
        val promoted = nextStageFrame?.takeIf { it.stageIndex == stageIndex }
        if (promoted == null) {
            nextStageFrame?.bitmap?.cancel()
        } else {
            frameBuffer.addLast(promoted)
            pendingFramePaths += promoted.choice.path
        }
        nextStageFrame = null
    }

    fun cancelPrefetchedFrames() {
        clearCurrentFrameBuffer()
        nextStageFrame?.bitmap?.cancel()
        nextStageFrame = null
    }

    fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                cancelPrefetchedFrames()
                frames.evictAll()
            }

            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                frames.trimToSize(frames.size() / 2)
            }
        }
    }

    override fun onCleared() {
        cancelPrefetchedFrames()
        displayedBitmaps.clear()
        previewPartIndex = null
        previewBitmap = null
        frames.evictAll()
    }

    private suspend fun awaitFirstCompletedFrame(
        candidates: List<PendingFrame>
    ): PendingFrame = select {
        candidates.forEach { pending ->
            pending.bitmap.onJoin { pending }
        }
    }

    private fun removePendingFrame(pending: PendingFrame) {
        frameBuffer.remove(pending)
        pendingFramePaths.remove(pending.choice.path)
    }
    /** Bỏ các request đã lỗi khỏi buffer, trả về số request bị bỏ. */
    private fun dropFailedFrames(): Int {
        var dropped = 0
        val iterator = frameBuffer.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.bitmap.isCancelled) {
                iterator.remove()
                pendingFramePaths.remove(pending.choice.path)
                slowFramePaths += pending.choice.path
                dropped++
            }
        }
        return dropped
    }
    private fun hasUnqueuedAlternative(slowPath: String): Boolean {
        val stage = session?.currentStage ?: return false
        return stage.choices.any { choice ->
            choice.path != slowPath &&
                choice.path !in pendingFramePaths &&
                choice.path !in slowFramePaths
        }
    }

    private fun prefetchNextStageFrame(
        activeSession: PauseChallengeSession,
        currentStageIndex: Int
    ) {
        val followingStageIndex = currentStageIndex + 1
        val followingStage = activeSession.stages.getOrNull(followingStageIndex)
        if (followingStage == null) {
            nextStageFrame?.bitmap?.cancel()
            nextStageFrame = null
        } else if (nextStageFrame?.stageIndex != followingStageIndex) {
            nextStageFrame?.bitmap?.cancel()
            val choice = followingStage.choices.random()
            nextStageFrame = PendingFrame(
                stageIndex = followingStageIndex,
                choice = choice,
                bitmap = viewModelScope.async { loadFrame(choice.path, Priority.IMMEDIATE) }
            )
        }
    }

    private fun nextBufferedChoice(): PauseChallengeSession.Choice? {
        val activeSession = session ?: return null
        val stage = activeSession.currentStage ?: return null
        val previous = activeSession.selectedChoices[stage.partIndex]
        var candidateCount = 0
        stage.choices.forEach { choice ->
            if (
                choice != previous &&
                choice.path !in pendingFramePaths &&
                choice.path !in slowFramePaths
            ) {
                candidateCount++
            }
        }
        if (candidateCount == 0) {
            if (frameBuffer.isNotEmpty()) return null

            val fallback = stage.choices.firstOrNull { choice ->
                choice.path !in pendingFramePaths && choice.path !in slowFramePaths
            }
            if (fallback != null) return fallback

            // Retry old URLs only after every different option has been attempted.
            slowFramePaths.clear()
            return activeSession.randomChoice().takeIf { it?.path !in pendingFramePaths }
        }

        var target = Random.nextInt(candidateCount)
        stage.choices.forEach { choice ->
            if (
                choice != previous &&
                choice.path !in pendingFramePaths &&
                choice.path !in slowFramePaths
            ) {
                if (target == 0) return choice
                target--
            }
        }
        return null
    }

    private fun clearCurrentFrameBuffer() {
        frameBuffer.forEach { it.bitmap.cancel() }
        frameBuffer.clear()
        pendingFramePaths.clear()
        slowFramePaths.clear()
        bufferedStageIndex = NO_STAGE
    }

    private suspend fun loadFrame(path: String, priority: Priority): Bitmap {
        return try {
            loadFrameAtSize(path, priority, frameSize)
        } catch (error: Throwable) {
            if (error is CancellationException || !error.isOutOfMemoryFailure()) throw error

            frames.evictAll()
            if (frameSize <= MIN_RETRY_FRAME_SIZE) {
                throw IllegalStateException("Out of memory while loading frame", error)
            }

            val retrySize = (frameSize / 2).coerceAtLeast(MIN_RETRY_FRAME_SIZE)
            try {
                loadFrameAtSize(path, priority, retrySize)
            } catch (retryError: Throwable) {
                if (retryError is CancellationException) throw retryError
                if (retryError.isOutOfMemoryFailure()) {
                    frames.evictAll()
                    throw IllegalStateException(
                        "Out of memory while retrying challenge frame",
                        retryError
                    )
                }
                throw retryError
            }
        }
    }

    private suspend fun loadFrameAtSize(
        path: String,
        priority: Priority,
        requestedSize: Int
    ): Bitmap {
        val cacheKey = "$requestedSize:$path"
        frames.get(cacheKey)?.let { return it }
        val manager = Glide.with(context)
        val target = manager.asBitmap().load(path)
            .priority(priority)
            // Random frames are usually viewed once. Cache the source and avoid an
            // additional transformed-file write; the decoded result is cached in RAM.
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .timeout(LOAD_TIMEOUT_MS)
            .submit(requestedSize, requestedSize)
        try {
            val bitmap = runInterruptible(Dispatchers.IO) {
                target.get().copy(Bitmap.Config.ARGB_8888, false)
                    ?: error("Cannot copy challenge frame")
            }
            frames.put(cacheKey, bitmap)
            return bitmap
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                manager.clear(target)
            }
        }
    }

    private fun Throwable.isOutOfMemoryFailure(): Boolean {
        var current: Throwable? = this
        var depth = 0
        while (current != null && depth < MAX_ERROR_CAUSE_DEPTH) {
            if (current is OutOfMemoryError) return true
            val next = current.cause
            if (next === current) break
            current = next
            depth++
        }
        return false
    }

    private companion object {
        private const val BYTES_PER_MEGABYTE = 1024L * 1024L
        private const val MIN_CACHE_BYTES = 8L * BYTES_PER_MEGABYTE
        private const val MAX_CACHE_BYTES = 32L * BYTES_PER_MEGABYTE
        private const val FRAME_SIZE = 768
        private const val LOW_RAM_FRAME_SIZE = 512
        private const val MIN_RETRY_FRAME_SIZE = 100
        private const val FRAME_BUFFER_SIZE = 4
        private const val LOW_RAM_FRAME_BUFFER_SIZE = 2
        private const val SLOW_FRAME_SWITCH_MS = 300L
        private const val LOAD_TIMEOUT_MS = 10_000
        private const val MAX_ERROR_CAUSE_DEPTH = 10
        private const val NO_STAGE = -1
        private const val MAX_LOAD_FAILURES = 3

        fun frameCacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 12L)
            .coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
            .toInt()
    }
}
