package com.oc.catemoji.catoc.ui.main.play

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.R
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    private val speechToText = SpeechToTextManager(appContext)
    private val appContextForOpponent = appContext.applicationContext
    private var screenActive = false
    private var playerReady = false
    private var countdownVisible = false
    private var countdownJob: Job? = null
    private var resultAdvanceJob: Job? = null
    private val _battleUiState = MutableStateFlow(PlayBattleUiState())
    val battleUiState = _battleUiState.asStateFlow()
    val useMicrophone get() = savedState.get<Boolean>(EXTRA_USE_MICROPHONE) ?: false
    val introFinished get() = savedState.get<Boolean>(INTRO_FINISHED) == true
    val battleStarted get() = savedState.get<Boolean>(BATTLE_STARTED) == true

    val player = repository.user
        .map<UserEntity?, RoomLoadState<PlayPlayerUiState?>> { user ->
            playerReady = user != null
            refreshBattleUiState()
            startCountdownIfReady()
            RoomLoadState.Success(user?.let(::PlayPlayerUiState))
        }
        .catch { error ->
            if (error is CancellationException) throw error
            playerReady = false
            refreshBattleUiState()
            emit(RoomLoadState.Error(error))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomLoadState.Loading)

    private val _microphone = MutableStateFlow(MicrophoneUiState())
    val microphone = _microphone.asStateFlow()

    private val _barkCount = MutableStateFlow(savedState.get<Int>("barks") ?: 0)
    val barkCount = _barkCount.asStateFlow()

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
    private val rewardedAdBusy = MutableStateFlow(false)
    val resultUiState = combine(phase, result, saving, saveError, rewardedAdBusy) { phase, result, saving, error, adBusy ->
        PlayResultUiState(phase, result?.let(PlayRewardUiState::from), saving || adBusy, error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayResultUiState(phase.value))
    private var resultJob: Job? = null
    private var bonusJob: Job? = null
    val isFinished get() = savedState.contains("won")
    val canLeaveResult get() = !_saving.value && !rewardedAdBusy.value && !_saveError.value
    val canClaimBonus get() = canLeaveResult && _result.value?.bonusClaimed == false

    fun onRewardedAdBusyChanged(busy: Boolean) {
        rewardedAdBusy.value = busy
    }

    fun onIntroFinished() {
        savedState[INTRO_FINISHED] = true
        startCountdownIfReady()
    }

    fun onScreenStarted(permissionGranted: Boolean) {
        screenActive = true
        val permissionRecovered = permissionGranted &&
            (savedState.get<Boolean>(MICROPHONE_BLOCKED) == true ||
                _microphone.value.status == MicrophoneStatus.PERMISSION_REQUIRED)
        if (permissionRecovered) {
            savedState[MICROPHONE_BLOCKED] = false
            stopListening()
        }
        val shouldRestart = savedState.get<Boolean>(MICROPHONE_WAS_ACTIVE) == true || permissionRecovered
        savedState[MICROPHONE_WAS_ACTIVE] = false
        if (useMicrophone && shouldRestart && battleStarted && !isFinished) {
            if (permissionGranted) startListening() else requireMicrophonePermission()
        }
        refreshBattleUiState()
        startCountdownIfReady()
        scheduleResultAdvance()
    }

    fun onScreenStopped(isFinishing: Boolean) {
        screenActive = false
        countdownJob?.cancel()
        countdownJob = null
        countdownVisible = false
        resultAdvanceJob?.cancel()
        resultAdvanceJob = null
        val status = _microphone.value.status
        savedState[MICROPHONE_WAS_ACTIVE] = !isFinishing &&
            (status == MicrophoneStatus.STARTING || status == MicrophoneStatus.LISTENING)
        stopListening()
        refreshBattleUiState()
    }

    private fun startCountdownIfReady() {
        if (!screenActive || !introFinished || !playerReady || battleStarted || isFinished ||
            countdownJob?.isActive == true) return
        countdownVisible = true
        refreshBattleUiState()
        countdownJob = viewModelScope.launch {
            while (countdownStep < 4) {
                delay(if (countdownStep < 3) 1_000L else 600L)
                savedState[COUNTDOWN_STEP] = countdownStep + 1
                refreshBattleUiState()
            }
            markBattleStarted()
        }
    }

    private val countdownStep get() = (savedState.get<Int>(COUNTDOWN_STEP) ?: 0).coerceIn(0, 4)

    private fun refreshBattleUiState() {
        val canPlay = screenActive && playerReady && battleStarted && !isFinished
        _battleUiState.value = PlayBattleUiState(
            countdownNumber = if (countdownVisible && countdownStep < 4) 3 - countdownStep else null,
            canPlay = canPlay,
            canTap = canPlay && !useMicrophone,
            runTapOpponent = canPlay && !useMicrophone,
        )
    }

    private fun scheduleResultAdvance() {
        if (!screenActive || phase.value != BattlePhase.GAME_OVER || _result.value == null ||
            resultAdvanceJob?.isActive == true) return
        resultAdvanceJob = viewModelScope.launch {
            delay(1_000)
            if (screenActive && phase.value == BattlePhase.GAME_OVER) advanceResult()
        }
    }

    fun markBattleStarted() {
        if (!savedState.contains("startedAt")) savedState["startedAt"] = android.os.SystemClock.elapsedRealtime()
        savedState[BATTLE_STARTED] = true
        savedState[COUNTDOWN_STEP] = 4
        countdownJob?.cancel()
        countdownJob = null
        countdownVisible = false
        refreshBattleUiState()
    }

    fun finishBattle(won: Boolean) {
        if (!isFinished) {
            savedState["won"] = won
            val started = savedState.get<Long>("startedAt") ?: android.os.SystemClock.elapsedRealtime()
            savedState["duration"] = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(1)
            savedState["phase"] = BattlePhase.GAME_OVER
        }
        countdownJob?.cancel()
        countdownJob = null
        countdownVisible = false
        savedState[MICROPHONE_WAS_ACTIVE] = false
        stopListening()
        refreshBattleUiState()
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
            } finally {
                _saving.value = false
                scheduleResultAdvance()
            }
        }
    }

    fun advanceResult() {
        val result = _result.value ?: return
        resultAdvanceJob?.cancel()
        resultAdvanceJob = null
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

    private val microphoneSession = Mutex()
    private var microphoneJob: Job? = null
    private var sessionId = 0

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
        if (!useMicrophone || !screenActive || !battleStarted || isFinished) return
        if (microphoneJob?.isActive == true) return
        savedState[MICROPHONE_WAS_ACTIVE] = true
        val session = ++sessionId
        _microphone.value = MicrophoneUiState(MicrophoneStatus.STARTING)

        microphoneJob = viewModelScope.launch {
            try {
                // Prevent overlapping AudioRecord instances on Stop / Start.
                microphoneSession.withLock {
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
                            level = update.level,
                            transcript = update.partialText ?: update.text,
                            isPartial = !update.isFinal,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                if (session == sessionId) requireMicrophonePermission()
            } catch (error: Exception) {
                Log.e("PlayMicrophone", "Could not recognize barking", error)
                if (session == sessionId) {
                    savedState[MICROPHONE_WAS_ACTIVE] = false
                    _microphone.value = MicrophoneUiState(
                        MicrophoneStatus.ERROR,
                        error = R.string.play_speech_error
                    )
                }
            }
        }
    }

    private fun stopListening() {
        sessionId++
        microphoneJob?.cancel()
        microphoneJob = null
        _microphone.value = MicrophoneUiState()
    }

    fun addPlayerTap() {
        if (_battleUiState.value.canTap) addPlayerPoints(1)
    }

    private fun addPlayerPoints(points: Int) {
        if (points <= 0 || isFinished) return
        _barkCount.value += points
        savedState["barks"] = _barkCount.value
    }

    /** Returns true only when the Activity needs to launch Android's permission dialog. */
    fun requestMicrophone(permissionGranted: Boolean): Boolean {
        if (!useMicrophone || !screenActive || !battleStarted || isFinished ||
            savedState.get<Boolean>(MICROPHONE_AUTO_REQUESTED) == true) return false
        savedState[MICROPHONE_AUTO_REQUESTED] = true
        when {
            permissionGranted -> startListening()
            savedState.get<Boolean>(MICROPHONE_BLOCKED) == true -> requireMicrophonePermission()
            else -> return true
        }
        return false
    }

    fun onMicrophonePermissionResult(granted: Boolean, blocked: Boolean) {
        savedState[MICROPHONE_BLOCKED] = blocked
        if (granted) startListening() else requireMicrophonePermission()
    }

    fun retryMicrophone() {
        savedState[MICROPHONE_AUTO_REQUESTED] = false
    }

    fun requireMicrophonePermission() {
        savedState[MICROPHONE_WAS_ACTIVE] = false
        stopListening()
        _microphone.value = MicrophoneUiState(
            MicrophoneStatus.PERMISSION_REQUIRED,
            error = R.string.play_microphone_permission
        )
    }

    companion object {
        const val EXTRA_USE_MICROPHONE = "play_use_microphone"
        private const val INTRO_FINISHED = "play_intro_finished"
        private const val BATTLE_STARTED = "play_battle_started"
        private const val COUNTDOWN_STEP = "play_countdown_step"
        private const val MICROPHONE_BLOCKED = "play_microphone_blocked"
        private const val MICROPHONE_AUTO_REQUESTED = "play_microphone_auto_requested"
        private const val MICROPHONE_WAS_ACTIVE = "play_microphone_was_active"
    }
}

enum class BattlePhase { PLAYING, GAME_OVER, BADGES, RANK, REWARD }

enum class MicrophoneStatus {
    IDLE, STARTING, LISTENING, PERMISSION_REQUIRED, ERROR
}

data class MicrophoneUiState(
    val status: MicrophoneStatus = MicrophoneStatus.IDLE,
    val level: Float = 0f,
    val error: Int? = null,
    val transcript: String = "",
    val isPartial: Boolean = false,
)
