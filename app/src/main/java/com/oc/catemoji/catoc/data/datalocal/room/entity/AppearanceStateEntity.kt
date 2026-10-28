package com.oc.catemoji.catoc.data.datalocal.room.entity

import androidx.room.Entity

/** Only progression is persisted; both catalogues and their images stay in assets. */
@Entity(tableName = "appearance_state", primaryKeys = ["type", "assetId"])
data class AppearanceStateEntity(
    val type: String,
    val assetId: String,
    val selected: Boolean = false,
    val unlock: Boolean = false,
) {
    companion object {
        const val AVATAR = "avatar"
        const val FRAME = "frame"
    }
}
