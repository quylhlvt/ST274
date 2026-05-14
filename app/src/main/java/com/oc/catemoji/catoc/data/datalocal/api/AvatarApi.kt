package com.oc.catemoji.catoc.data.datalocal.api

import android.util.Log
import com.oc.catemoji.catoc.data.model.custom.*
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// ── CONFIG ────────────────────────────────────────────────────────────────────

object ApiConfig {
    const val BASE_URL_1   = "https://lvtglobal.tech/"
    const val BASE_URL_2   = "https://lvt-api-tech.io.vn/"
    const val BASE_CONNECT = "public/app/ST229_CatAvatarCatMaker/"

    // URL đang active (thay đổi khi fallback)
    var BASE_URL = BASE_URL_1
}

// ── RESPONSE WRAPPER ──────────────────────────────────────────────────────────

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data")    val data: T?          = null,
    @SerializedName("message") val message: String?  = null,
    @SerializedName("code")    val code: Int          = 200
)

// ── API MODEL ─────────────────────────────────────────────────────────────────

data class X10(
    @SerializedName("colorArray") val colorArray: String,
    @SerializedName("parts")      val parts: String,
    @SerializedName("position")   val position: String,
    @SerializedName("quantity")   val quantity: String,
    @SerializedName("level")      val level: String
) {
    val quantityInt: Int get() = quantity.toIntOrNull() ?: 0
    val levelInt: Int   get() = level.toIntOrNull() ?: Int.MAX_VALUE
}
// ── SERVICE ───────────────────────────────────────────────────────────────────

interface AvatarApiService {
    @GET("api/app/ST229_CatAvatarCatMaker")
    suspend fun getAllData(): Map<String, List<X10>>
}

// ── MAPPER ────────────────────────────────────────────────────────────────────

object ApiTemplateMapper {

    fun map(raw: Map<String, List<X10>>): List<CustomModel> {
        val base = ApiConfig.BASE_URL
        val conn = ApiConfig.BASE_CONNECT

        return raw.entries
            .sortedBy { (_, list) -> list.firstOrNull()?.levelInt ?: Int.MAX_VALUE }
            .map { (key, list) ->
                val bodyParts = list
                    .sortedBy { it.parts.substringBefore("-").toIntOrNull() ?: 999 }
                    .map { x10 ->
                        val parts = x10.parts.split("-")
                        val x = parts.getOrNull(0)?.toIntOrNull() ?: 0  // số TRƯỚC "-"
                        val y = parts.getOrNull(1)?.toIntOrNull() ?: 0  // số SAU "-"

                        val (colors, listThumbPath) = buildColorsAndThumbs(x10, base, conn)
                        BodyPartModel(
                            nav           = "${base}${conn}${x10.position}/${x10.parts}/nav.png",
                            listPath      = colors,
                            listThumbPath = listThumbPath,
                            position      = x,
                            zIndex        = y
                        )
                    }

                // FIX: dùng bp.position (= x = số TRƯỚC "-") để phân biệt nav0
                // position == 1 → body chính (nav0): chỉ "dice", không "none"
                // position >= 2 → nav khác: "none" + "dice"
                bodyParts.forEach { bp ->
                    bp.listPath.forEach { cm ->
                        if (cm.listPath.isEmpty()) return@forEach  // guard
                        when {
                            bp.zIndex == 1 -> {
                                // Body chính: chỉ dice
                                if (cm.listPath.first() != "dice") cm.listPath.add(0, "dice")
                            }
                            else -> {
                                // Nav khác: none + dice
                                if (cm.listPath.first() != "none") {
                                    cm.listPath.add(0, "none")
                                    cm.listPath.add(1, "dice")
                                }
                            }
                        }
                    }
                }

                CustomModel(
                    id         = "online_$key",
                    avatar     = "${base}${conn}$key/avatar.png",
                    listPath   = ArrayList(bodyParts),
                    selections = arrayListOf(),
                    updatedAt  = System.currentTimeMillis()
                )
            }
    }

    private fun buildColorsAndThumbs(
        x10: X10,
        base: String,
        conn: String
    ): Pair<ArrayList<ColorModel>, ArrayList<String>> {
        val colors        = arrayListOf<ColorModel>()
        val listThumbPath = arrayListOf<String>()
        val qty           = x10.quantityInt
        val halfQty       = maxOf(1, qty / 2)

        if (x10.colorArray.isEmpty()) {
            for (i in 1..halfQty) {
                listThumbPath.add("${base}${conn}${x10.position}/${x10.parts}/thumb_$i.png")
            }
            val realPaths = (1..halfQty).map { i ->
                "${base}${conn}${x10.position}/${x10.parts}/$i.png"
            }
            colors.add(ColorModel("", ArrayList(realPaths)))
        } else {
            for (i in 1..halfQty * 2 + 1) {
                listThumbPath.add("${base}${conn}${x10.position}/${x10.parts}/thumb_$i.png")
            }
            x10.colorArray.split(",").forEach { color ->
                val paths = (1..qty).map { i ->
                    "${base}${conn}${x10.position}/${x10.parts}/$color/$i.png"
                }
                colors.add(ColorModel(color, ArrayList(paths)))
            }
        }

        // KHÔNG add "none"/"dice" ở đây — để map() xử lý sau khi có đủ thông tin position
        return colors to listThumbPath
    }
}

// ── RESULT SEALED CLASS ───────────────────────────────────────────────────────

sealed class ApiResult<out T> {
    data class Success<T>(val data: T)         : ApiResult<T>()
    data class Error(val message: String)      : ApiResult<Nothing>()
}

// ── REMOTE DATA SOURCE ────────────────────────────────────────────────────────
@Singleton
class RemoteDataSource @Inject constructor(
    private val apiHelper: ApiHelper   // ← đổi sang ApiHelper
) {
    companion object { private const val TAG = "RemoteDataSource" }

    suspend fun fetchTemplates(): ApiResult<List<CustomModel>> {
        return withContext(Dispatchers.IO) {
            try {
                ApiConfig.BASE_URL = ApiConfig.BASE_URL_1
                val body = apiHelper.api1.getAllData()
                Log.d(TAG, "✅ URL1 success, size: ${body.size}")
                ApiResult.Success(ApiTemplateMapper.map(body))
            } catch (e: Exception) {
                Log.e(TAG, "❌ URL1 failed, trying URL2: ${e.message}")
                try {
                    ApiConfig.BASE_URL = ApiConfig.BASE_URL_2
                    val body = apiHelper.api2.getAllData()
                    Log.d(TAG, "✅ URL2 success, size: ${body.size}")
                    ApiResult.Success(ApiTemplateMapper.map(body))
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ URL2 failed: ${e2.message}")
                    ApiResult.Error(e2.message ?: "Network error")
                }
            }
        }
    }
}