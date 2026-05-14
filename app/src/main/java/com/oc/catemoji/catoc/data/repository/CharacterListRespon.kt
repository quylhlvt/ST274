package com.oc.catemoji.catoc.data.repository

import android.graphics.Color
import android.os.Message
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.google.gson.annotations.SerializedName

data class CharacterListRespon(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<CharactersData>?,
    @SerializedName("messeage") val message: String
    )

data class CharactersData(
    @SerializedName("id") val id: String,
    @SerializedName("avatar") val avatar: String,
    @SerializedName("data1") val bodyParts: List<BodyPartData>
    )
data class BodyPartData(
    @SerializedName("nav") val nav: String,
    @SerializedName("colors") val colors: List<ColorData>
    )
data class ColorData(
    @SerializedName("color_name") val colorName: String,
    @SerializedName("paths") val colors: List<String>
    )
