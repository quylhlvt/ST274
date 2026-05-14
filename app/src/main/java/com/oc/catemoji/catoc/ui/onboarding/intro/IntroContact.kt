package com.oc.catemoji.catoc.ui.onboarding.intro

import androidx.annotation.StringRes
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.model.intro.IntroModel

class IntroContact {
}
data class IntroUiState(
    val pagesSplash: List<IntroModel>? = emptyList(),
    val page: Int = 0,
    @StringRes val textButtonRes: Int = R.string.next
)

sealed class IntroSingleEvent {
    data object NavigateToNextScreen : IntroSingleEvent()
}