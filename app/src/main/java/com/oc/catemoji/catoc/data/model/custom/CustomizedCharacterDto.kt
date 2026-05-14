// data/model/custom/CustomizedCharacterDto.kt
package com.oc.catemoji.catoc.data.model.custom

/**
 * DTO nhẹ — chỉ lưu vào customized.json.
 * listPath KHÔNG lưu — reconstruct từ template khi cần.
 */
data class CustomizedCharacterDto(
    val id:         String                    = "",
    val templateId: String?                   = null,
    val avatar:     String                    = "",  // thumbnail để hiển thị
    val selections: ArrayList<SelectionIndex> = arrayListOf(),
    val imageSave:  String                    = "",  // path ảnh render đã lưu
    val isFlipped:  Boolean                   = false,
    val updatedAt:  Long                      = System.currentTimeMillis(),
    val createdAt:  Long                      = System.currentTimeMillis() // ← THÊM

)

// Extension convert 2 chiều
fun CustomModel.toDto() = CustomizedCharacterDto(
    id         = id,
    templateId = templateId,
    avatar     = avatar,
    selections = ArrayList(selections),
    imageSave  = imageSave,
    isFlipped  = isFlipped,
    updatedAt  = updatedAt,
    createdAt  = createdAt
)

fun CustomizedCharacterDto.toModel(templateListPath: ArrayList<BodyPartModel> = arrayListOf()) = CustomModel(
    id         = id,
    templateId = templateId,
    avatar     = avatar,
    listPath   = templateListPath,  // inject từ template khi load
    selections = ArrayList(selections),
    imageSave  = imageSave,
    isFlipped  = isFlipped,
    updatedAt  = updatedAt,
    createdAt  = createdAt
)