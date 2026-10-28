package com.oc.catemoji.catoc.ui.main.editProfile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.AVATAR as AVATAR_TYPE
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.FRAME as FRAME_TYPE
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.ProfileAppearanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileUiState(
    val loading: Boolean = true,
    val ready: Boolean = false,
    val name: String = "",
    val avatar: String = "",
    val frame: String = "",
    val avatars: List<String> = emptyList(),
    val frames: List<String> = emptyList(),
    val unlockedAvatars: Set<String> = emptySet(),
    val unlockedFrames: Set<String> = emptySet(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val nameRequired: Boolean = false,
    val error: Int? = null,
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val repository: BattleRepository,
    private val appearances: ProfileAppearanceRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val user = checkNotNull(repository.user.first()) { "User profile does not exist" }
                val options = appearances.loadOptions()
                val avatars = options.avatars
                val frames = options.frames
                appearances.initializeOptions(options, user.imageAvatar, user.imageFrame)
                val states = appearances.states.first()
                val avatarStates = states.filter { it.type == AVATAR_TYPE }
                val frameStates = states.filter { it.type == FRAME_TYPE }
                val unlockedAvatars = avatarStates.filter { it.unlock }.map { it.image }.intersect(avatars.toSet())
                val unlockedFrames = frameStates.filter { it.unlock }.map { it.image }.intersect(frames.toSet())
                val name = savedState.get<String>(NAME) ?: user.name
                val avatar = savedState.get<String>(AVATAR)?.takeIf { it in unlockedAvatars }
                    ?: avatarStates.firstOrNull { it.selected && it.image in unlockedAvatars }?.image
                    ?: unlockedAvatars.first()
                val frame = savedState.get<String>(FRAME)?.takeIf { it in unlockedFrames }
                    ?: frameStates.firstOrNull { it.selected && it.image in unlockedFrames }?.image
                    ?: unlockedFrames.first()
                _state.value = EditProfileUiState(
                    loading = false, ready = true, name = name, avatar = avatar, frame = frame,
                    avatars = avatars, frames = frames, unlockedAvatars = unlockedAvatars, unlockedFrames = unlockedFrames,
                )
                appearances.states.collect { items ->
                    val avatarUnlocks = items.filter { it.type == AVATAR_TYPE && it.unlock }
                        .map { it.image }.intersect(avatars.toSet())
                    val frameUnlocks = items.filter { it.type == FRAME_TYPE && it.unlock }
                        .map { it.image }.intersect(frames.toSet())
                    _state.update { it.copy(unlockedAvatars = avatarUnlocks, unlockedFrames = frameUnlocks) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = EditProfileUiState(loading = false, error = R.string.home_profile_load_failed)
            }
        }
    }

    fun setName(name: String) {
        if (!canEdit()) return
        savedState[NAME] = name
        _state.value = _state.value.copy(name = name, nameRequired = false, error = null)
    }

    fun selectAvatar(avatar: String) {
        if (!canEdit() || avatar !in _state.value.unlockedAvatars) return
        savedState[AVATAR] = avatar
        _state.value = _state.value.copy(avatar = avatar, error = null)
    }

    fun selectFrame(frame: String) {
        if (!canEdit() || frame !in _state.value.unlockedFrames) return
        savedState[FRAME] = frame
        _state.value = _state.value.copy(frame = frame, error = null)
    }

    fun save() {
        if (!canEdit()) return
        val draft = _state.value
        if (draft.name.isBlank()) {
            _state.value = draft.copy(nameRequired = true, error = null)
            return
        }
        _state.value = draft.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                repository.updateProfile(draft.name, draft.avatar, draft.frame)
                _state.value = _state.value.copy(saving = false, saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(saving = false, error = R.string.profile_save_failed)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun canEdit() = _state.value.let { it.ready && !it.saving && !it.saved }

    private companion object {
        const val NAME = "profile_name"
        const val AVATAR = "profile_avatar"
        const val FRAME = "profile_frame"
    }
}
