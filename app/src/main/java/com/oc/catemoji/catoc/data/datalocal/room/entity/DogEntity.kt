package com.oc.catemoji.catoc.data.datalocal.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dog")
data class DogEntity(
    @PrimaryKey(autoGenerate = true) val iddog: Int = 0,
    val stepRank: Int = 0,
    val progress: Int = 0,
    val unlock: Boolean = false,
    val selected: Boolean = false,
)
