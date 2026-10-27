package com.oc.catemoji.catoc.data.datalocal.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameBatter: String,
    /** Unix timestamp in milliseconds. Format for the current locale in the UI. */
    val date: Long,
    val imageAvatar1: String,
    val imageAvatar2: String,
    val typePlay: String,
)
