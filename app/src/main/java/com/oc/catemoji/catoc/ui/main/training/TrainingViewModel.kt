package com.oc.catemoji.catoc.ui.main.training

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.audio.BarkDetectionManager
import com.oc.catemoji.catoc.data.audio.BarkDetectionUpdate
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import com.oc.catemoji.catoc.data.repository.TrainingEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class TrainingViewModel @Inject constructor(
    assets: TrainingAssetRepository,
    @ApplicationContext context: Context,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val barkDetection = BarkDetectionManager(context)
    private val sessionMutex = Mutex()
    private var microphoneJob: Job? = null
    private var sessionId = 0
    private val _effects = MutableStateFlow<List<TrainingEffect>>(emptyList())
    val effects = _effects.asStateFlow()
    private val _background = MutableStateFlow<String?>(null)
    val background = _background.asStateFlow()
    private val _barkCount = MutableStateFlow(savedState.get<Int>("barks") ?: 0)
    val barkCount = _barkCount.asStateFlow()
    private val _microphoneError = MutableStateFlow<Int?>(null)
    val microphoneError = _microphoneError.asStateFlow()
    var consumedBarks: Int
        get() = savedState["consumedBarks"] ?: 0
        set(value) { savedState["consumedBarks"] = value }

    init {
        viewModelScope.launch {
            _effects.value = assets.loadEffects()
            _background.value = assets.loadBackgrounds().randomOrNull()
        }
    }

    fun addPlayerTap() = addBarks(1)

    /** One confirmed acoustic event owns one point and one visual effect. */
    internal fun acceptDetection(update: BarkDetectionUpdate) {
        if (update.detected) addBarks(1)
    }

    private fun addBarks(count: Int) {
        if (count <= 0) return
        _barkCount.value += count
        savedState["barks"] = _barkCount.value
    }

    fun startListening() {
        if (microphoneJob?.isActive == true) return
        val session = ++sessionId
        _microphoneError.value = null
        microphoneJob = viewModelScope.launch {
            try {
                sessionMutex.withLock {
                    barkDetection.listen().collect { update ->
                        if (session == sessionId) acceptDetection(update)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("TrainingAudio", "Could not recognize barking", error)
                if (session == sessionId) _microphoneError.value = if (error is SecurityException)
                    R.string.play_microphone_permission else R.string.play_bark_detection_error
            }
        }
    }

    fun stopListening() {
        sessionId++
        microphoneJob?.cancel()
        microphoneJob = null
    }
}
