package com.oc.catemoji.catoc.ui.main.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.audio.BarkDetection
import com.oc.catemoji.catoc.data.audio.BarkDetectionPolicy
import com.oc.catemoji.catoc.data.audio.DeviceSoundClassifier
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
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
) : ViewModel() {
    // Hilt injects the application Context; classifier is owned by this ViewModel.
    // This avoids the unresolved DeviceSoundClassifier type in KAPT constructor stubs.
    private val soundClassifier = DeviceSoundClassifier(appContext)
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

    private val microphoneSession = Mutex()
    private var microphoneJob: Job? = null
    private var sessionId = 0

    fun startListening() {
        if (microphoneJob?.isActive == true) return
        val session = ++sessionId
        val baseCount = _barkCount.value
        _microphone.value = MicrophoneUiState(MicrophoneStatus.STARTING)

        microphoneJob = viewModelScope.launch {
            try {
                // Prevent overlapping AudioRecord instances on Stop / Start.
                microphoneSession.withLock {
                    val policy = BarkDetectionPolicy(
                        barkClassIndex = BARK_CLASS_INDEX,
                        threshold = 0.75f,
                        requiredMatches = 1
                    )
                    soundClassifier.listenScored().collect { update ->
                        if (session != sessionId) return@collect
                        val detection = policy.evaluate(update.prediction)
                        // Count comes from separate PCM bursts, not AI's TRUE state.
                        _barkCount.value = baseCount + update.totalPoints
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
                if (session == sessionId) {
                    _microphone.value = MicrophoneUiState(
                        MicrophoneStatus.ERROR,
                        error = R.string.play_microphone_error
                    )
                }
            }
        }
    }

    fun stopListening() {
        sessionId++
        microphoneJob?.cancel()
        microphoneJob = null
        _microphone.value = MicrophoneUiState()
    }

    fun requireMicrophonePermission() {
        _microphone.value = MicrophoneUiState(
            MicrophoneStatus.PERMISSION_REQUIRED,
            error = R.string.play_microphone_permission
        )
    }

    companion object {
        // Confirmed with the current labels.txt: 1 Class 3 = human bark.
        const val BARK_CLASS_INDEX = 1
    }
}

enum class MicrophoneStatus {
    IDLE, STARTING, LISTENING, PERMISSION_REQUIRED, ERROR
}

data class MicrophoneUiState(
    val status: MicrophoneStatus = MicrophoneStatus.IDLE,
    val detection: BarkDetection? = null,
    val error: Int? = null,
)