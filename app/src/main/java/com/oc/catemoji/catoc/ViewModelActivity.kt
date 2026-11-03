package com.oc.catemoji.catoc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.data.datalocal.room.entity.*
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.DogSkinRoster
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
    val dogPreview: String?,
    val dogHomeImage: String?,
)

private data class DogStateImages(val preview: String, val active: String)

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

            val images = selectedDog?.let { dog ->
                DogSkinRoster(user, dogs).appearanceFor(dog.iddog)?.let { skin ->
                    DogStateImages(skin.statePreview, skin.statePlaying)
                } ?: resolveLegacyDogImages(user?.imageAvatar.orEmpty(), dog.iddog, dog.stepRank)
            }

            RoomLoadState.Success(
                SharedHomeData(
                    user = user,
                    selectedDog = selectedDog,
                    dogPreview = images?.preview,
                    dogHomeImage = images?.active,
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

    private val legacyDogImagesCache = mutableMapOf<String, DogStateImages?>()

    private suspend fun resolveLegacyDogImages(
        avatar: String,
        dogId: Int,
        stepRank: Int,
    ): DogStateImages? {
        val prefix = "file:///android_asset/"
        val fallback = avatar.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
            ?.substringBeforeLast('/', "").orEmpty()
        val cacheKey = "$dogId:$stepRank:$fallback"
        if (legacyDogImagesCache.containsKey(cacheKey)) {
            return legacyDogImagesCache[cacheKey]
        }

        val images = withContext(Dispatchers.IO) {
            try {
                val stages = if (stepRank > 1) listOf("adult", "2. adult") else listOf("puppy", "1. puppy")
                listOf(dogId.toString(), fallback).distinct().filter { it.isNotBlank() }
                    .firstNotNullOfOrNull { directory ->
                        stages.firstNotNullOfOrNull { stage ->
                            val path = "$directory/state/$stage"
                            val files = context.assets.list(path).orEmpty()
                            if ("1.webp" in files) {
                                val preview = "$prefix$path/1.webp"
                                val active = if ("2.webp" in files) "$prefix$path/2.webp" else preview
                                DogStateImages(preview, active)
                            } else null
                        }
                    }
            } catch (e: java.io.IOException) {
                null
            }
        }

        legacyDogImagesCache[cacheKey] = images
        return images
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
