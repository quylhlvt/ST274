
package com.oc.catemoji.catoc.ui.main.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.repository.TrainingAssetRepository
import com.oc.catemoji.catoc.data.repository.TrainingEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val assets: TrainingAssetRepository,
) : ViewModel() {
    private var effects: List<TrainingEffect> = emptyList()

    private val _background = MutableStateFlow<String?>(null)
    val background = _background.asStateFlow()

    init {
        viewModelScope.launch {
            effects = assets.loadEffects()
            _background.value = assets.loadBackgrounds().randomOrNull()
        }
    }

    fun randomEffect(): TrainingEffect? {
        return effects.randomOrNull()
    }
}
