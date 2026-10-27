package com.oc.catemoji.catoc.data.datalocal.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    /** URI, local path or URL; the image itself is not stored in SQLite. */
    val imageAvatar: String = "",
    val imageFrame: String = "",
    val coinbone: Int = 0,
    val rank: Int = 0,
    val rankbatter: Int = 0,
    val iddog: Int = 0,
)
