package com.oc.catemoji.catoc.data.model.custom

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID
import kotlin.math.abs

// ─────────────────────────────────────────────
//  SelectionIndex
//  Lưu lựa chọn CHỈ bằng 3 số nguyên.
//  ~12 bytes thay vì ~60 bytes path string.
// ─────────────────────────────────────────────
@Parcelize
data class SelectionIndex(
    val bodyPartIndex: Int = 0,
    val colorIndex:    Int = 0,
    val pathIndex:     Int = 0
) : Parcelable

@Parcelize
data class LayerTransform(
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotation: Float = 0f
) : Parcelable {
    fun isDefault(): Boolean =
        abs(scale - 1f) <= DEFAULT_EPSILON &&
            abs(translationX) <= DEFAULT_EPSILON &&
            abs(translationY) <= DEFAULT_EPSILON &&
            abs(rotation) <= DEFAULT_EPSILON

    companion object {
        // Reversing scale steps can leave a tiny Float rounding error around 1f.
        private const val DEFAULT_EPSILON = 0.0001f
    }
}

// ─────────────────────────────────────────────
//  ColorModel
// ─────────────────────────────────────────────
@Parcelize
data class ColorModel(
    val color:    String            = "",
    val listPath: ArrayList<String> = arrayListOf()
) : Parcelable

// ─────────────────────────────────────────────
//  BodyPartModel
// ─────────────────────────────────────────────
@Parcelize
data class BodyPartModel(
    val nav:            String               = "",
    val listPath:       ArrayList<ColorModel> = arrayListOf(),
val listThumbPath:  ArrayList<String>    = arrayListOf(),
val listSinglePath: ArrayList<String>    = arrayListOf(),
val position:       Int                  = 0,
val zIndex:         Int                  = 0
) : Parcelable

// ─────────────────────────────────────────────
//  CustomModel
//  Template hoặc customized character.
//  [selections] chỉ lưu INDEX – không lưu path string.
// ─────────────────────────────────────────────
@Parcelize
data class CustomModel(
    val id:         String                     = UUID.randomUUID().toString(),
    val templateId: String? = null,
    val avatar:     String                     = "",
    val listPath:   ArrayList<BodyPartModel>   = arrayListOf(),
    val selections: ArrayList<SelectionIndex>  = arrayListOf(),
    val imageSave:  String                     = "",
    val isFlipped:  Boolean                    = false,
    val layerTransforms: Map<Int, LayerTransform> = emptyMap(),
    val createdAt:  Long                       = 0L,
    val updatedAt:  Long                       = System.currentTimeMillis(),
    val level:      Int                        = 0
) : Parcelable {
    val bodyPartCount: Int get() = listPath.size
    fun isTemplate() = id.startsWith("template_") || id.startsWith("online_")
    fun hasPreviewImage() = imageSave.isNotEmpty()
    fun getBodyPart(index: Int) = listPath.getOrNull(index)
    fun getSelection(navIndex: Int) = selections.getOrNull(navIndex)

    fun hasValidSelectionsFor(template: CustomModel): Boolean {
        val parts = template.listPath.sortedBy { it.zIndex }
        if (selections.size > parts.size) return false

        return selections.withIndex().all { (index, selection) ->
            val part = parts.getOrNull(index) ?: return false
            val color = part.listPath.getOrNull(selection.colorIndex) ?: return false
            color.listPath.getOrNull(selection.pathIndex) != null
        }
    }
}
