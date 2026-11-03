package com.oc.catemoji.catoc.data.repository

import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.AVATAR
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.model.DogSkinCatalogue
import com.oc.catemoji.catoc.data.model.DogSkinOption
import com.oc.catemoji.catoc.data.model.statistics.DogBattleStatistics

internal class DogSkinRecords(val puppy: DogEntity?, val adult: DogEntity?) {
    val statistics = DogBattleStatistics(
        played = (puppy?.matchesPlayed ?: 0) + (adult?.matchesPlayed ?: 0),
        wins = (puppy?.matchesWon ?: 0) + (adult?.matchesWon ?: 0),
    )

    fun progressFor(skin: DogSkinOption): Int =
        statistics.progressTo(skin.winsToMaxLevel)
}

internal class DogSkinRoster(user: UserEntity?, dogs: List<DogEntity>) {
    private val byId = dogs.associateBy { it.iddog }
    private val legacyDog = byId[user?.iddog]?.takeIf { DogSkinCatalogue.findById(it.iddog) == null }
    private val legacySkin = legacyDog?.let { dog ->
        appearanceAssetId(AVATAR, user?.imageAvatar.orEmpty())?.toIntOrNull()
            ?.takeIf { it in 1..DogSkinCatalogue.FOLDER_COUNT }
            ?.let { DogSkinOption(it, if (dog.stepRank > 1) 2 else 1, price = 0) }
    }

    fun recordsFor(skin: DogSkinOption): DogSkinRecords = DogSkinRecords(
        puppy = savedDog(skin.copy(imageNumber = 1)),
        adult = savedDog(skin.copy(imageNumber = 2)),
    )

    fun appearanceFor(dogId: Int): DogSkinOption? {
        val skin = DogSkinCatalogue.findById(dogId)
            ?: legacySkin?.takeIf { legacyDog?.iddog == dogId }
            ?: return null
        val puppy = skin.copy(imageNumber = 1)
        return puppy.atProgress(recordsFor(puppy).progressFor(puppy))
    }

    private fun savedDog(skin: DogSkinOption): DogEntity? =
        byId[skin.id] ?: legacyDog?.takeIf { legacySkin?.id == skin.id }
}
