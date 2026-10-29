
package com.oc.catemoji.catoc.ui.main.training

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

data class TrainingEffect(
    val folder: Int,
    val path: String
)

@HiltViewModel
class TrainingViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val assetContext = context.applicationContext

    private var effects: List<TrainingEffect> = emptyList()

    private val _background = MutableStateFlow<String?>(null)
    val background = _background.asStateFlow()

    init {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val loadedEffects = (1..3).flatMap { folder ->
                    imagePaths("imgEffect/$folder").map { path ->
                        TrainingEffect(
                            folder = folder,
                            path = path
                        )
                    }
                }

                val randomBackground =
                    imagePaths("bg").randomOrNull()

                loadedEffects to randomBackground
            }

            effects = result.first
            _background.value = result.second
        }
    }

    fun randomEffect(): TrainingEffect? {
        return effects.randomOrNull()
    }

    private fun imagePaths(
        directory: String
    ): List<String> {

        val files = try {
            assetContext.assets.list(directory).orEmpty()
        } catch (_: IOException) {
            emptyArray()
        }

        return files
            .filter { filename ->
                filename.substringAfterLast('.')
                    .lowercase() in IMAGE_EXTENSIONS
            }
            .sorted()
            .map { filename ->
                "file:///android_asset/$directory/$filename"
            }
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf(
            "png", "webp", "jpg", "jpeg"
        )
    }
}
