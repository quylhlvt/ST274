package com.oc.catemoji.catoc

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Process-scoped state used by MainActivity to avoid repeating image preload. */
@HiltViewModel
class ViewModelActivity @Inject constructor() : ViewModel() {
    private val _imagesReady = MutableStateFlow(false)
    val imagesReady: StateFlow<Boolean> = _imagesReady.asStateFlow()

    fun notifyImagesReady() {
        _imagesReady.value = true
    }
}
