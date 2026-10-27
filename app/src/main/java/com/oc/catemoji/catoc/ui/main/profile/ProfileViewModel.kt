package com.oc.catemoji.catoc.ui.main.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.repository.ProfileRepository
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {
    val statistics = repository.statistics.map { summary ->
        DataLocal.statisticsList.map { item ->
            item.copy(count = when (item.name) {
                R.string.total -> summary.total.toDouble()
                R.string.win -> summary.wins.toDouble()
                R.string.lost -> summary.losses.toDouble()
                R.string.rate -> summary.winRate
                R.string.streak -> summary.streak.toDouble()
                else -> 0.0
            })
        }
    }
    val history = repository.history
    val achievements = repository.achievements
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed = _loadFailed.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.seedDemoItems()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _loadFailed.value = true
            }
        }
    }
}

