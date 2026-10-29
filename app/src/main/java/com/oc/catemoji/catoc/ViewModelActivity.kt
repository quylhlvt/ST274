package com.oc.catemoji.catoc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.datalocal.room.entity.*
import com.oc.catemoji.catoc.data.repository.BattleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface RoomLoadState<out T> {
    data object Loading : RoomLoadState<Nothing>

    data class Success<T>(
        val data: T
    ) : RoomLoadState<T>

    data class Error(
        val cause: Throwable
    ) : RoomLoadState<Nothing>
}

data class SharedHomeData(
    val user: UserEntity?,
    val selectedDog: DogEntity?,
    val dogPreview: String?
)

@HiltViewModel
class ViewModelActivity @Inject constructor(
    private val repository: BattleRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _barkEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
    val barkEvents = _barkEvents.asSharedFlow()

    fun notifyBarkDetected() {
        _barkEvents.tryEmit(Unit)
    }

    // ROOM - DÙNG CHUNG
    val user = repository.user.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val dogs = repository.dogs.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    val history = repository.history.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    // Trạng thái Home đầy đủ.
    // Loading phân biệt với trường hợp user thực sự null.
    val homeState: StateFlow<RoomLoadState<SharedHomeData>> =
        combine(repository.user, repository.dogs) { user, dogs ->
            val selectedDog = dogs.firstOrNull {
                it.iddog == user?.iddog &&
                        it.unlock &&
                        it.selected
            }

            val preview = if (selectedDog != null) {
                resolveDogPreview(user?.imageAvatar.orEmpty())
            } else {
                null
            }

            RoomLoadState.Success(
                SharedHomeData(
                    user = user,
                    selectedDog = selectedDog,
                    dogPreview = preview
                )
            ) as RoomLoadState<SharedHomeData>
        }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(RoomLoadState.Error(error))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                RoomLoadState.Loading
            )

    private val dogPreviewCache = mutableMapOf<String, String?>()

    private suspend fun resolveDogPreview(
        avatar: String
    ): String? {
        val prefix = "file:///android_asset/"
        if (!avatar.startsWith(prefix)) return null

        val directory = avatar.removePrefix(prefix)
            .substringBeforeLast('/', "")

        if (directory.isBlank()) return null

        if (dogPreviewCache.containsKey(directory)) {
            return dogPreviewCache[directory]
        }

        val preview = withContext(Dispatchers.IO) {
            try {
                // The first two dogs use "puppy"; the remaining assets use "1. puppy".
                listOf("puppy", "1. puppy").firstNotNullOfOrNull { stage ->
                    val path = "$directory/state/$stage"
                    if ("1.webp" in context.assets.list(path).orEmpty()) {
                        "$prefix$path/1.webp"
                    } else {
                        null
                    }
                }
            } catch (e: java.io.IOException) {
                null
            }
        }

        dogPreviewCache[directory] = preview
        return preview
    }

    // IMAGE PRELOAD
    private val _imagesReady = MutableStateFlow(false)
    val imagesReady = _imagesReady.asStateFlow()

    fun notifyImagesReady() {
        _imagesReady.value = true
    }

    // ROOM ACTIONS
    suspend fun renameUser(name: String) {
        repository.renameUser(name)
    }

    suspend fun addBones(amount: Int) {
        repository.addBones(amount)
    }
    suspend fun addBattleRank(amount: Int) {
        repository.addBattleRank(amount)
    }

    suspend fun saveUser(user: UserEntity) {
        repository.saveUser(user)
    }

    suspend fun saveDog(dog: DogEntity) {
        repository.saveDog(dog)
    }

    suspend fun saveDogs(dogs: List<DogEntity>) {
        repository.saveDogs(dogs)
    }

    suspend fun addHistory(history: HistoryEntity): Long {
        return repository.addHistory(history)
    }

    suspend fun deleteHistory(history: HistoryEntity) {
        repository.deleteHistory(history)
    }

    suspend fun clearHistory() {
        repository.clearHistory()
    }
}
