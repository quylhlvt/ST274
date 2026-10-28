package com.oc.catemoji.catoc.ui.main.play

import androidx.lifecycle.ViewModel
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.audio.BarkDetection
import com.oc.catemoji.catoc.data.audio.BarkDetectionPolicy
import com.oc.catemoji.catoc.data.audio.BarkEventCounter
import com.oc.catemoji.catoc.data.audio.DeviceSoundClassifier
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val soundClassifier: DeviceSoundClassifier,
) : ViewModel() {
    val player = repository.user
        .map<UserEntity?, RoomLoadState<UserEntity?>> { RoomLoadState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(RoomLoadState.Error(error))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomLoadState.Loading)

    private val _microphone = MutableStateFlow(MicrophoneUiState())
    val microphone = _microphone.asStateFlow()
    private val barkCounter = BarkEventCounter()
    private val _barkCount = MutableStateFlow(0)
    val barkCount = _barkCount.asStateFlow()
    private val microphoneSession = Mutex()
    private var microphoneJob: Job? = null
    private var sessionId = 0

    fun startListening() {
        if (microphoneJob?.isActive == true) return
        val session = ++sessionId
        _microphone.value = MicrophoneUiState(MicrophoneStatus.STARTING)
        microphoneJob = viewModelScope.launch {
            try {
                // A rapid Stop/Start waits until the previous recorder has released its microphone.
                microphoneSession.withLock {
                    val policy = BarkDetectionPolicy(BARK_CLASS_INDEX)
                    soundClassifier.listen().collect { prediction ->
                        if (session == sessionId) {
                            val detection = policy.evaluate(prediction)
                            _barkCount.value = barkCounter.update(detection.isBark, SystemClock.elapsedRealtime())
                            _microphone.value = MicrophoneUiState(
                                MicrophoneStatus.LISTENING, detection,
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                if (session == sessionId) requireMicrophonePermission()
            } catch (error: Exception) {
                if (session == sessionId) {
                    _microphone.value = MicrophoneUiState(MicrophoneStatus.ERROR, error = R.string.play_microphone_error)
                }
            }
        }
    }

    fun stopListening() {
        sessionId++
        microphoneJob?.cancel()
        microphoneJob = null
        barkCounter.endBurst()
        _microphone.value = MicrophoneUiState()
    }

    fun requireMicrophonePermission() {
        _microphone.value = MicrophoneUiState(
            MicrophoneStatus.PERMISSION_REQUIRED, error = R.string.play_microphone_permission,
        )
    }

    companion object {
        // The requested target label in the supplied export is "0 Background Noise".
        const val BARK_CLASS_INDEX = 0
    }
}

enum class MicrophoneStatus { IDLE, STARTING, LISTENING, PERMISSION_REQUIRED, ERROR }

data class MicrophoneUiState(
    val status: MicrophoneStatus = MicrophoneStatus.IDLE,
    val detection: BarkDetection? = null,
    val error: Int? = null,
)
