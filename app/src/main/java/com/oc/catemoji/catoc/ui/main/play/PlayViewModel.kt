package com.oc.catemoji.catoc.ui.main.play

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.audio.BarkDetection
import com.oc.catemoji.catoc.data.audio.BarkDetectionPolicy
import com.oc.catemoji.catoc.data.audio.DeviceSoundClassifier
import com.oc.catemoji.catoc.data.audio.BarkSpeechCounter
import com.oc.catemoji.catoc.data.audio.SpeechToTextManager
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class PlayViewModel @Inject constructor(
    repository: BattleRepository,
    @ApplicationContext appContext: Context,
    trainingAssets: TrainingAssetRepository,
) : ViewModel() {
    // Hilt injects the application Context; classifier is owned by this ViewModel.
    // This avoids the unresolved DeviceSoundClassifier type in KAPT constructor stubs.
    private val soundClassifier = DeviceSoundClassifier(appContext)
    private val speechToText = SpeechToTextManager(appContext)
    val player = repository.user
        .map<UserEntity?, RoomLoadState<UserEntity?>> { RoomLoadState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(RoomLoadState.Error(error))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomLoadState.Loading)

    private val _microphone = MutableStateFlow(MicrophoneUiState())
    val microphone = _microphone.asStateFlow()

    private val _barkCount = MutableStateFlow(0)
    val barkCount = _barkCount.asStateFlow()

    private val _previewBarkCount = MutableStateFlow(0)
    val previewBarkCount = _previewBarkCount.asStateFlow()

    private val _detectionMode = MutableStateFlow(BarkDetectionMode.WORDS)
    val detectionMode = _detectionMode.asStateFlow()

    private val _background = MutableStateFlow<String?>(null)
    val background = _background.asStateFlow()

    private val microphoneSession = Mutex()
    private var microphoneJob: Job? = null
    private var sessionId = 0
    private var microphoneEnabled = true

    init {
        viewModelScope.launch {
            _background.value = trainingAssets.loadBackgrounds().randomOrNull()
        }
    }

    fun startListening() {
        if (!microphoneEnabled) return
        if (microphoneJob?.isActive == true) return
        val session = ++sessionId
        val mode = _detectionMode.value
        _microphone.value = MicrophoneUiState(MicrophoneStatus.STARTING)

        microphoneJob = viewModelScope.launch {
            try {
                // Prevent overlapping AudioRecord instances on Stop / Start.
                microphoneSession.withLock {
                    if (mode == BarkDetectionMode.WORDS) {
                        val counter = BarkSpeechCounter()
                        speechToText.listen().collect { update ->
                            if (session != sessionId) return@collect
                            val previousCount = counter.count
                            counter.accept(update.text, update.isFinal, update.words)
                            addPlayerPoints(counter.count - previousCount)
                            if (counter.count != previousCount || update.isFinal) {
                                Log.d("BarkSpeechScore", "${if (update.isFinal) "result" else "partial"} " +
                                    "added=${counter.count - previousCount} total=${_barkCount.value}")
                            }
                            _microphone.value = MicrophoneUiState(
                                status = MicrophoneStatus.LISTENING,
                                detection = BarkDetection(null, counter.count > previousCount, update.level),
                                transcript = update.text,
                                isPartial = !update.isFinal,
                            )
                        }
                        return@withLock
                    }
                    val policy = BarkDetectionPolicy(
                        barkClassIndex = DeviceSoundClassifier.BARK_CLASS_INDEX,
                        threshold = DeviceSoundClassifier.BARK_SCORE_THRESHOLD,
                        requiredMatches = 2
                    )
                    var countedSoundPoints = 0
                    soundClassifier.listenScored().collect { update ->
                        if (session != sessionId) return@collect
                        val detection = policy.evaluate(update.prediction)
                        // Count comes from separate PCM bursts, not AI's TRUE state.
                        // PCM levels and inference results arrive from two workers.
                        // An older level snapshot must not lower/replay the count.
                        if (update.totalPoints > countedSoundPoints) {
                            addPlayerPoints(update.totalPoints - countedSoundPoints)
                            countedSoundPoints = update.totalPoints
                        }
                        _microphone.value = MicrophoneUiState(
                            MicrophoneStatus.LISTENING,
                            detection
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                if (session == sessionId) requireMicrophonePermission()
            } catch (error: Exception) {
                Log.e("PlayMicrophone", "Could not listen in $mode mode", error)
                if (session == sessionId) {
                    _previewBarkCount.value = _barkCount.value
                    _microphone.value = MicrophoneUiState(
                        MicrophoneStatus.ERROR,
                        error = if (mode == BarkDetectionMode.WORDS) R.string.play_speech_error
                            else R.string.play_microphone_error
                    )
                }
            }
        }
    }

    fun stopListening() {
        sessionId++
        microphoneJob?.cancel()
        microphoneJob = null
        _previewBarkCount.value = _barkCount.value
        _microphone.value = MicrophoneUiState()
    }

    fun addPlayerTap() = addPlayerPoints(1)

    fun setMicrophoneEnabled(enabled: Boolean) {
        microphoneEnabled = enabled
        if (!enabled) stopListening()
    }

    private fun addPlayerPoints(points: Int) {
        if (points <= 0) return
        _barkCount.value += points
        _previewBarkCount.value = _barkCount.value
    }

    fun selectDetectionMode(mode: BarkDetectionMode) {
        if (_detectionMode.value == mode) return
        val restart = microphoneJob?.isActive == true
        stopListening()
        _detectionMode.value = mode
        if (restart) startListening()
    }

    fun requireMicrophonePermission() {
        stopListening()
        _microphone.value = MicrophoneUiState(
            MicrophoneStatus.PERMISSION_REQUIRED,
            error = R.string.play_microphone_permission
        )
    }
}

enum class BarkDetectionMode { SOUND, WORDS }

enum class MicrophoneStatus {
    IDLE, STARTING, LISTENING, PERMISSION_REQUIRED, ERROR
}

data class MicrophoneUiState(
    val status: MicrophoneStatus = MicrophoneStatus.IDLE,
    val detection: BarkDetection? = null,
    val error: Int? = null,
    val transcript: String = "",
    val isPartial: Boolean = false,
)
