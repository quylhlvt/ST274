package com.oc.catemoji.catoc.utils.state

sealed class SaveState {
    data class Success(val path: String) : SaveState()
    data class Error(val exception: Exception) : SaveState()
    object Loading : SaveState()
}