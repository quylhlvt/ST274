package com.oc.catemoji.catoc.ui.onboarding.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor() : ViewModel() {
    private val _navigateSignal = MutableStateFlow(false)
    val navigateSignal: StateFlow<Boolean> = _navigateSignal.asStateFlow()

    private var splashJob: Job? = null

    fun startSplashTimer() {
        if (splashJob != null) return
        splashJob = viewModelScope.launch {
            delay(SPLASH_DURATION_MS)
            _navigateSignal.value = true
        }
    }

    private companion object {
        const val SPLASH_DURATION_MS = 3_000L
    }
}
