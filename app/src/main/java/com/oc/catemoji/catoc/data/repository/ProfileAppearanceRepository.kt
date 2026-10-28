package com.oc.catemoji.catoc.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.AVATAR
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.FRAME
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ProfileAppearanceOptions(val avatars: List<String>, val frames: List<String>)

data class ProfileAppearanceState(val type: String, val image: String, val selected: Boolean, val unlock: Boolean)

internal fun appearanceAssetId(type: String, image: String): String? {
    val id = image.removePrefix("file:///android_asset/").removeSuffix("/$type.png")
    return id.takeIf { it.toIntOrNull() != null && image == "file:///android_asset/$it/$type.png" }
}

class ProfileAppearanceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    val states = database.appearanceDao().observeStates().map { items ->
        items.map { ProfileAppearanceState(it.type, "file:///android_asset/${it.assetId}/${it.type}.png", it.selected, it.unlock) }
    }

    suspend fun loadOptions(): ProfileAppearanceOptions = withContext(Dispatchers.IO) {
        val avatars = mutableListOf<String>()
        val frames = mutableListOf<String>()
        context.assets.list("").orEmpty()
            .filter { it.toIntOrNull() != null }
            .sortedBy { it.toInt() }
            .forEach { directory ->
                val files = context.assets.list(directory).orEmpty()
                if ("avatar.png" in files) avatars += "file:///android_asset/$directory/avatar.png"
                if ("frame.png" in files) frames += "file:///android_asset/$directory/frame.png"
            }
        check(avatars.isNotEmpty() && frames.isNotEmpty()) { "Profile appearance assets missing" }
        ProfileAppearanceOptions(avatars, frames)
    }

    suspend fun initializeOptions(options: ProfileAppearanceOptions, appliedAvatar: String, appliedFrame: String) =
        database.withTransaction {
            initializeType(AVATAR, options.avatars, appliedAvatar)
            initializeType(FRAME, options.frames, appliedFrame)
        }

    private suspend fun initializeType(type: String, images: List<String>, appliedImage: String) {
        require(images.isNotEmpty())
        val dao = database.appearanceDao()
        val availableIds = images.mapNotNull { appearanceAssetId(type, it) }.toSet()
        if (dao.getStates(type).any { it.selected && it.unlock && it.assetId in availableIds }) return
        val image = appliedImage.takeIf { it in images } ?: images.first()
        val id = checkNotNull(appearanceAssetId(type, image))
        dao.insertIfMissing(AppearanceStateEntity(type, id, unlock = true))
        dao.unlock(type, id)
        dao.clearSelection(type)
        check(dao.selectUnlocked(type, id) == 1)
    }

    /** Gameplay/reward rules grant an item without selecting or applying it. */
    suspend fun unlockAvatar(image: String) = unlock(AVATAR, image)

    suspend fun unlockFrame(image: String) = unlock(FRAME, image)

    private suspend fun unlock(type: String, image: String) {
        val options = loadOptions()
        require(image in if (type == AVATAR) options.avatars else options.frames) { "Unknown appearance asset" }
        val id = checkNotNull(appearanceAssetId(type, image))
        database.withTransaction {
            database.appearanceDao().insertIfMissing(AppearanceStateEntity(type, id, unlock = true))
            database.appearanceDao().unlock(type, id)
        }
    }
}
