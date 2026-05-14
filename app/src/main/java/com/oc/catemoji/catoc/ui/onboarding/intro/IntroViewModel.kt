package com.oc.catemoji.catoc.ui.onboarding.intro

import androidx.lifecycle.ViewModel
import androidx.work.Data
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.core.helper.SharedPreferencesManager
import com.oc.catemoji.catoc.utils.DataLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import javax.inject.Inject
@HiltViewModel
class IntroViewModel @Inject constructor( private val sharedPreferences: SharedPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntroUiState())
    val uiState: StateFlow<IntroUiState> get() = _uiState

    private val _singleEvent = MutableStateFlow<IntroSingleEvent?>(null)
    val singleEvent: Flow<IntroSingleEvent> get() = _singleEvent.filterNotNull()

    init {
        getData()
//        checkSplashScreenStatus()
    }

    private fun getData() {
        val list = DataLocal.itemIntroList
        _uiState.update { state ->
            state.copy(pagesSplash = list)
        }
        getPage(currentPage = 0, totalPages = list.size)
    }


    private fun navigateToNextScreen() {
        _singleEvent.value = IntroSingleEvent.NavigateToNextScreen
    }

    fun getPage(currentPage: Int, totalPages: Int) {
        val textRes =
            if (currentPage >= totalPages - 1)
                R.string.continue_per
            else
                R.string.next

        _uiState.update {
            it.copy(
                page = currentPage,
                textButtonRes = textRes
            )
        }
    }

    fun nextPage(currentPage: Int, totalPages: Int) {
        val isLastPage = currentPage >= totalPages - 1
        if (isLastPage) {
            navigateToNextScreen()
        } else {
            _uiState.update { state ->
                state.copy(
                    page = currentPage + 1
                )
            }
        }
    }


}