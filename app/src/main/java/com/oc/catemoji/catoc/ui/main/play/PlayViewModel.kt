package com.oc.catemoji.catoc.ui.main.play

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
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
import com.oc.catemoji.catoc.data.repository.TrainingEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import java.util.UUID
import com.oc.catemoji.catoc.data.datalocal.room.entity.BattleResultEntity

@HiltViewModel
class PlayViewModel @Inject constructor(
    private val repository: BattleRepository,
    @ApplicationContext appContext: Context,
    trainingAssets: TrainingAssetRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
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

    private val _barkCount = MutableStateFlow(savedState.get<Int>("barks") ?: 0)
    val barkCount = _barkCount.asStateFlow()

    private val _previewBarkCount = MutableStateFlow(0)
    val previewBarkCount = _previewBarkCount.asStateFlow()

    private val _detectionMode = MutableStateFlow(BarkDetectionMode.WORDS)
    val detectionMode = _detectionMode.asStateFlow()

    private val _background = MutableStateFlow<String?>(null)
    val background = _background.asStateFlow()
    private val _effects = MutableStateFlow<List<TrainingEffect>>(emptyList())
    val effects = _effects.asStateFlow()

    private val matchId = savedState.get<String>("matchId") ?: UUID.randomUUID().toString().also {
        savedState["matchId"] = it
    }
    val opponentId = savedState.get<Int>("opponentId") ?: (2..15).random().also {
        savedState["opponentId"] = it
    }
    val opponentAvatar get() = "file:///android_asset/$opponentId/avatar.webp"
    val opponentFrame get() = "file:///android_asset/$opponentId/frame.webp"
    var consumedBarks: Int
        get() = savedState["consumedBarks"] ?: 0
        set(value) { savedState["consumedBarks"] = value }
    var trackProgress: Float
        get() = savedState["trackProgress"] ?: 0.5f
        set(value) { savedState["trackProgress"] = value }
    private val _result = MutableStateFlow<BattleResultEntity?>(null)
    val result = _result.asStateFlow()
    val phase = savedState.getStateFlow("phase", BattlePhase.PLAYING)
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveError = MutableStateFlow(false)
    val saveError = _saveError.asStateFlow()
    private var resultJob: Job? = null
    private var bonusJob: Job? = null
    val isFinished get() = savedState.contains("won")

    fun markBattleStarted() {
        if (!savedState.contains("startedAt")) savedState["startedAt"] = android.os.SystemClock.elapsedRealtime()
    }

    fun finishBattle(won: Boolean) {
        if (!isFinished) {
            savedState["won"] = won
            val started = savedState.get<Long>("startedAt") ?: android.os.SystemClock.elapsedRealtime()
            savedState["duration"] = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(1)
            savedState["phase"] = BattlePhase.GAME_OVER
        }
        stopListening()
        saveBattleResult()
    }

    fun saveBattleResult() {
        val won = savedState.get<Boolean>("won") ?: return
        if (resultJob?.isActive == true || _result.value != null) return
        _saving.value = true
        _saveError.value = false
        resultJob = viewModelScope.launch {
            try {
                _result.value = repository.completeBattle(matchId, won,
                    appContextForOpponent.getString(R.string.play_opponent_name), opponentAvatar,
                    _barkCount.value, savedState["duration"] ?: 1L)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("PlayResult", "Could not save battle", error)
                _saveError.value = true
            } finally { _saving.value = false }
        }
    }

    fun advanceResult() {
        val result = _result.value ?: return
        savedState["phase"] = when (phase.value) {
            BattlePhase.GAME_OVER -> if (result.badges().isNotEmpty()) BattlePhase.BADGES
                else if (result.nextRank > result.previousRank) BattlePhase.RANK else BattlePhase.REWARD
            BattlePhase.BADGES -> if (result.nextRank > result.previousRank) BattlePhase.RANK else BattlePhase.REWARD
            BattlePhase.RANK -> BattlePhase.REWARD
            else -> phase.value
        }
    }

    /** Called only from the rewarded ad's earned callback. */
    fun claimEarnedBonus() {
        if (_result.value?.bonusClaimed != false || bonusJob?.isActive == true) return
        savedState["bonusEarned"] = true
        _saving.value = true
        _saveError.value = false
        bonusJob = viewModelScope.launch {
            try {
                _result.value = repository.claimBattleBonus(matchId)
                savedState["bonusEarned"] = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("PlayResult", "Could not save earned bonus", error)
                _saveError.value = true
            } finally { _saving.value = false }
        }
    }

    fun retryResultSave() {
        if (savedState.get<Boolean>("bonusEarned") == true) claimEarnedBonus() else saveBattleResult()
    }

    private val appContextForOpponent = appContext.applicationContext

    private val microphoneSession = Mutex()
    private var microphoneJob: Job? = null
    private var sessionId = 0
    private var microphoneEnabled = true

    init {
        viewModelScope.launch {
            _effects.value = trainingAssets.loadEffects()
            _background.value = trainingAssets.loadBackgrounds().randomOrNull()
        }
        if (isFinished) {
            saveBattleResult()
            viewModelScope.launch {
                result.first { it != null }
                if (savedState.get<Boolean>("bonusEarned") == true) claimEarnedBonus()
            }
        }
    }

    fun startListening() {
        if (!microphoneEnabled || isFinished) return
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
                            counter.accept(update.text, update.isFinal, update.words, update.partialText)
                            addPlayerPoints(counter.count - previousCount)
                            if (counter.count != previousCount || update.isFinal) {
                                Log.d("BarkSpeechScore", "${if (update.isFinal) "result" else "partial"} " +
                                    "added=${counter.count - previousCount} total=${_barkCount.value}")
                            }
                            _microphone.value = MicrophoneUiState(
                                status = MicrophoneStatus.LISTENING,
                                detection = BarkDetection(null, counter.count > previousCount, update.level),
                                transcript = update.partialText ?: update.text,
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
        if (points <= 0 || isFinished) return
        _barkCount.value += points
        savedState["barks"] = _barkCount.value
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

enum class BattlePhase { PLAYING, GAME_OVER, BADGES, RANK, REWARD }

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
