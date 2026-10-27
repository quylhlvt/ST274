package com.oc.catemoji.catoc.ui.main.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val user: UserEntity? = null,
    val dog: DogEntity? = null,
    val dogPreview: String? = null,
    val loadFailed: Boolean = false,
)

enum class HomeEvent { NAME_SAVED, NAME_REQUIRED, SAVE_FAILED, BONE_ADD_FAILED }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: BattleRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val dogPreviews = mutableMapOf<String, String?>()
    private val _renaming = MutableStateFlow(false)
    val renaming = _renaming.asStateFlow()
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(repository.user, repository.dogs) { user, dogs ->
        val selectedDog = dogs.firstOrNull {
            it.iddog == user?.iddog && it.unlock && it.selected
        }
        HomeUiState(
            loading = false,
            user = user,
            dog = selectedDog,
            dogPreview = if (selectedDog != null) resolveDogPreview(user?.imageAvatar.orEmpty()) else null,
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(HomeUiState(loading = false, loadFailed = true))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // Assets are linked to the stored avatar folder, never to the generated dog ID.
    // The initial Home character is the first frame of the puppy state.
    private suspend fun resolveDogPreview(avatar: String): String? {
        val prefix = "file:///android_asset/"
        if (!avatar.startsWith(prefix)) return null
        val directory = avatar.removePrefix(prefix).substringBeforeLast('/', "")
        if (directory.isEmpty()) return null
        if (dogPreviews.containsKey(directory)) return dogPreviews[directory]
        val preview = withContext(Dispatchers.IO) {
            try {
                val files = context.assets.list("$directory/state/puppy").orEmpty()
                files.firstOrNull { it == "1.png" }
                    ?.let { "$prefix$directory/state/puppy/$it" }
            } catch (error: java.io.IOException) {
                null
            }
        }
        dogPreviews[directory] = preview
        return preview
    }

    fun add100Bones() {
        viewModelScope.launch {
            try {
                repository.addBones(100)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.send(HomeEvent.BONE_ADD_FAILED)
            }
        }
    }

    fun renameUser(name: String) {
        if (_renaming.value) return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            _events.trySend(HomeEvent.NAME_REQUIRED)
            return
        }
        _renaming.value = true
        viewModelScope.launch {
            try {
                repository.renameUser(trimmedName)
                _events.send(HomeEvent.NAME_SAVED)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.send(HomeEvent.SAVE_FAILED)
            } finally {
                _renaming.value = false
            }
        }
    }
}
