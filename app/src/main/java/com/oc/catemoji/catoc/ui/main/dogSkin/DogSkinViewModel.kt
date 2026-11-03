package com.oc.catemoji.catoc.ui.main.dogSkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.model.DogSkinOption
import com.oc.catemoji.catoc.data.repository.DogSkinRepository
import com.oc.catemoji.catoc.data.repository.DogSkinRecords
import com.oc.catemoji.catoc.data.repository.DogSkinRoster
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DogSkinItem(
    val skin: DogSkinOption,
    val progress: Int,
    val matchesPlayed: Long,
    val matchesWon: Long,
    val locked: Boolean,
    val canAfford: Boolean,
    val selected: Boolean,
    val equipSkinId: Int = skin.id,
) {
    val image: String get() = skin.atProgress(progress).image
    val isMaxLevel: Boolean get() = progress == 100

    companion object {
        fun from(
            skin: DogSkinOption,
            dog: DogEntity?,
            coinbone: Int,
            selectedDogId: Int?,
            adultDog: DogEntity? = null,
        ): DogSkinItem {
            val owned = dog?.unlock == true || adultDog?.unlock == true || skin.id == 1
            val records = DogSkinRecords(dog, adultDog)
            val selected = listOfNotNull(dog, adultDog).any {
                (it.unlock || it.iddog == 1) && it.selected && it.iddog == selectedDogId
            }
            val equipSkinId = if (dog?.unlock != true && adultDog?.unlock == true) {
                skin.copy(imageNumber = 2).id
            } else {
                skin.id
            }
            return DogSkinItem(
                skin = skin,
                progress = records.progressFor(skin),
                matchesPlayed = records.statistics.played,
                matchesWon = records.statistics.wins,
                locked = !owned,
                canAfford = coinbone >= skin.price,
                selected = selected,
                equipSkinId = equipSkinId,
            )
        }
    }
}

data class DogSkinUiState(
    val loading: Boolean = true,
    val hasUser: Boolean = false,
    val coinbone: Int = 0,
    val items: List<DogSkinItem> = emptyList(),
    val selecting: Boolean = false,
    val claimingReward: Boolean = false,
    val error: Int? = null,
)

@HiltViewModel
class DogSkinViewModel @Inject constructor(
    private val repository: BattleRepository,
    private val skins: DogSkinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DogSkinUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val options = skins.loadOptions().filter { it.imageNumber == 1 }
                combine(repository.user, repository.dogs) { user, dogs ->
                    val roster = DogSkinRoster(user, dogs)
                    DogSkinUiState(
                        loading = false,
                        hasUser = user != null,
                        coinbone = user?.coinbone ?: 0,
                        items = options.map { skin ->
                            val records = roster.recordsFor(skin)
                            DogSkinItem.from(
                                skin, records.puppy, user?.coinbone ?: 0, user?.iddog,
                                adultDog = records.adult,
                            )
                        },
                    )
                }.collect { next -> _state.update {
                    next.copy(selecting = it.selecting, claimingReward = it.claimingReward)
                } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = DogSkinUiState(loading = false, error = R.string.dog_skin_load_failed)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun claimBoneReward() {
        val current = _state.value
        if (!current.hasUser || current.loading || current.claimingReward) return
        _state.update { it.copy(claimingReward = true, error = null) }
        viewModelScope.launch {
            try {
                repository.addBones(100)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(error = R.string.home_add_bones_failed) }
            } finally {
                _state.update { it.copy(claimingReward = false) }
            }
        }
    }

    fun onSkinClicked(skinId: Int) {
        val current = _state.value
        val item = current.items.firstOrNull { it.skin.id == skinId } ?: return
        if (!current.hasUser || current.loading || current.selecting || item.selected ||
            (item.locked && !item.canAfford)) return
        _state.update { it.copy(selecting = true, error = null) }
        viewModelScope.launch {
            try {
                if (item.locked) skins.purchase(skinId) else skins.equip(item.equipSkinId)
                _state.update { it.copy(selecting = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(selecting = false, error = if (item.locked)
                    R.string.dog_skin_purchase_failed else R.string.dog_skin_select_failed) }
            }
        }
    }
}
