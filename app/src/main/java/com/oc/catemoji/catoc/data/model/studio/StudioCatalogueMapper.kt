package com.oc.catemoji.catoc.data.model.studio

import android.net.Uri
import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.ColorModel
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import org.json.JSONObject

object StudioCatalogueMapper {
    const val JSON_URL =
        "https://lvtglobal.site/public/app/ST038_EmojiMaker_v2/studio/studio.json"

    private const val IMAGE_BASE_URL =
        "https://lvtglobal.site/public/app/ST038_EmojiMaker_v2/studio"

    /**
     * Metadata nhẹ lấy từ studio.json. Không tải hay decode ảnh tại bước này.
     */
    private data class CategoryConfig(
        val folderName: String,
        val quantity: Int,
        val level: Int
    )

    fun map(json: String): CustomModel {
        return CustomModel(
            id = "studio",
            listPath = mapBodyParts(json),
            level = 0
        )
    }

    /**
     * Mỗi phần tử là một item của nav. Số phần tử trong listPath của item
     * chính xác bằng quantity; Glide sẽ tự tải ảnh khi item xuất hiện.
     */
    fun mapBodyParts(json: String): ArrayList<BodyPartModel> {
        val root = JSONObject(json)
        val configs = root.keys().asSequence().mapNotNull { key ->
            val config = root.optJSONArray(key)?.optJSONObject(0) ?: return@mapNotNull null

            val quantity = config.optInt("quantity", 0)
            if (quantity <= 0) return@mapNotNull null

            val level = config.optInt("level", Int.MAX_VALUE)
            val folderName = config.optString("category").takeIf { it.isNotBlank() } ?: key

            CategoryConfig(folderName, quantity, level)
        }.sortedBy(CategoryConfig::level).toList()

        return buildBodyParts(configs)
    }

    private fun buildBodyParts(configs: List<CategoryConfig>): ArrayList<BodyPartModel> {
        return configs.mapTo(ArrayList(configs.size)) { config ->
            val categoryUrl = "$IMAGE_BASE_URL/${Uri.encode(config.folderName)}"
            val imageUrls = (1..config.quantity).mapTo(ArrayList(config.quantity)) { index ->
                "$categoryUrl/$index.png"
            }

            BodyPartModel(
                nav = imageUrls.firstOrNull().orEmpty(),
                listPath = arrayListOf(ColorModel(listPath = ArrayList(imageUrls))),
                listThumbPath = ArrayList(imageUrls),
                position = config.level,
                zIndex = config.level
            )
        }
    }
}
