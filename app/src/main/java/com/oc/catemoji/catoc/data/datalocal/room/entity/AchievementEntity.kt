package com.oc.catemoji.catoc.data.datalocal.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievement")
data class AchievementEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val image: String,
)
