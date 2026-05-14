package com.oc.catemoji.catoc.ui.onboarding.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
@HiltViewModel
class SplashViewModel @Inject constructor() : ViewModel() {

    private val _readyToNavigate = MutableSharedFlow<Unit>(
        replay = 1,  // Replay lại event khi collect muộn
        extraBufferCapacity = 0
    )
    val readyToNavigate = _readyToNavigate.asSharedFlow()

    private var isTimerRunning = false

    fun startSplashTimer(
        hasOnlineTemplates: Boolean,
        waitForOnline: suspend () -> Unit,
        waitForImages: suspend () -> Unit
    ) {
        if (isTimerRunning) return
        isTimerRunning = true

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val dataJob = launch {
                if (!hasOnlineTemplates) {
                    withTimeoutOrNull(8_000L) { waitForOnline() }
                }
            }
            val imagesJob = launch {
                withTimeoutOrNull(8_000L) { waitForImages() }  // timeout tránh treo
            }

            dataJob.join()
            imagesJob.join()

            val elapsed = System.currentTimeMillis() - startTime
            val remaining = 2_000L - elapsed
            if (remaining > 0) delay(remaining)

            isTimerRunning = false
            _readyToNavigate.emit(Unit)  // Emit event
        }
    }
}