package com.oc.catemoji.catoc.ui.onboarding.splash

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor() : ViewModel() {
    private val _navigateSignal = MutableStateFlow(false)
    val navigateSignal: StateFlow<Boolean> = _navigateSignal.asStateFlow()

    private val _dataReadySignal = MutableStateFlow(false)
    val dataReadySignal: StateFlow<Boolean> = _dataReadySignal.asStateFlow()

    private val createdAt = SystemClock.elapsedRealtime()
    private var splashJob: Job? = null
    private var navigateJob: Job? = null

    fun triggerNavigate() {
        if (_navigateSignal.value || navigateJob?.isActive == true) return
        navigateJob = viewModelScope.launch {
            val remaining = MIN_SPLASH_MS - (SystemClock.elapsedRealtime() - createdAt)
            if (remaining > 0) delay(remaining)
            _navigateSignal.value = true
        }
    }

    fun startSplashTimer(
        hasNetwork: Boolean,
        hasOnlineTemplates: Boolean,
        templatesFlow: StateFlow<List<CustomModel>>,
        imagesReadyFlow: StateFlow<Boolean>,
        localDataReadyFlow: StateFlow<Boolean>
    ) {
        if (splashJob != null) return

        splashJob = viewModelScope.launch {
            if (!hasNetwork) {
                delay(MIN_SPLASH_MS)
                _dataReadySignal.value = true
                _navigateSignal.value = true
                return@launch
            }

            val startTime = SystemClock.elapsedRealtime()
            withTimeoutOrNull(LOCAL_DATA_TIMEOUT_MS) {
                localDataReadyFlow.first { it }
            }

            val templatesJob = launch {
                if (!hasOnlineTemplates) {
                    withTimeoutOrNull(API_TIMEOUT_MS) {
                        templatesFlow.first { templates ->
                            templates.any { it.id.startsWith("online_") }
                        }
                    }
                }
            }
            val imagesJob = launch {
                withTimeoutOrNull(IMAGES_TIMEOUT_MS) {
                    imagesReadyFlow.first { it }
                }
            }
            templatesJob.join()
            imagesJob.join()

            val remaining = DATA_READY_MIN_MS - (SystemClock.elapsedRealtime() - startTime)
            if (remaining > 0) delay(remaining)
            _dataReadySignal.value = true
        }
    }

    private companion object {
        const val MIN_SPLASH_MS = 5_000L
        const val DATA_READY_MIN_MS = 3_000L
        const val LOCAL_DATA_TIMEOUT_MS = 5_000L
        const val API_TIMEOUT_MS = 8_000L
        const val IMAGES_TIMEOUT_MS = 30_000L
    }
}
