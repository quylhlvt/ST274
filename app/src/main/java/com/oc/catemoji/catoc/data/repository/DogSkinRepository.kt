package com.oc.catemoji.catoc.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.AVATAR
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity
import com.oc.catemoji.catoc.data.model.DogSkinCatalogue
import com.oc.catemoji.catoc.data.model.DogSkinOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DogSkinRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    private val options by lazy {
        DogSkinCatalogue.create(context.resources.getInteger(R.integer.dog_skin_price_step))
    }

    fun loadOptions(): List<DogSkinOption> = options

    suspend fun purchase(skinId: Int) {
        val skin = checkNotNull(options.firstOrNull { it.id == skinId }) { "Unknown dog skin" }
        database.withTransaction {
            val user = checkNotNull(database.userDao().getUser()) { "User profile does not exist" }
            val saved = savedDog(skin, user)
            if (saved?.unlock == true) return@withTransaction
            check(user.coinbone >= skin.price) { "Not enough CoinBone" }
            database.dogDao().upsert((saved ?: DogEntity(iddog = skin.id, stepRank = 1))
                .copy(unlock = true, selected = false))
            database.userDao().upsert(user.copy(coinbone = user.coinbone - skin.price))
            val avatarId = skin.folderId.toString()
            database.appearanceDao().insertIfMissing(AppearanceStateEntity(AVATAR, avatarId, unlock = true))
            database.appearanceDao().unlock(AVATAR, avatarId)
        }
    }

    suspend fun equip(skinId: Int) {
        val skin = checkNotNull(options.firstOrNull { it.id == skinId }) { "Unknown dog skin" }
        database.withTransaction {
            val user = checkNotNull(database.userDao().getUser()) { "User profile does not exist" }
            val dogs = database.dogDao()
            val saved = savedDog(skin, user)
            check(saved?.unlock == true || skin.id == 1) { "Dog skin must be purchased first" }
            dogs.clearSelection()
            dogs.upsert((saved ?: DogEntity(iddog = skin.id, stepRank = 1))
                .copy(unlock = true, selected = true))
            database.userDao().upsert(user.copy(iddog = skin.id))
        }
    }

    private suspend fun savedDog(skin: DogSkinOption, user: UserEntity): DogEntity? {
        val dogs = database.dogDao()
        dogs.getDog(skin.id)?.let { return it }
        return dogs.getDog(user.iddog)?.takeIf { dog ->
            options.none { it.id == dog.iddog } &&
                appearanceAssetId(AVATAR, user.imageAvatar)?.toIntOrNull() == skin.folderId &&
                skin.imageNumber == if (dog.stepRank > 1) 2 else 1
        }?.copy(iddog = skin.id)
    }
}
