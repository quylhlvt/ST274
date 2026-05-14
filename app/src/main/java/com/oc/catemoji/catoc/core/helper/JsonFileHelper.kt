package com.oc.catemoji.catoc.core.helper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object JsonFileHelper {

    private const val FILE_NAME = "characters.json"
    private val gson = Gson()

    private fun getFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun saveCharacters(context: Context, data: List<Any>) {
        val file = getFile(context)
        file.writeText(gson.toJson(data))
    }

    fun <T> loadCharacters(
        context: Context,
        typeToken: TypeToken<T>
    ): T? {
        val file = getFile(context)
        if (!file.exists()) return null
        return gson.fromJson(file.readText(), typeToken.type)
    }

    fun clear(context: Context) {
        val file = getFile(context)
        if (file.exists()) file.delete()
    }
}
