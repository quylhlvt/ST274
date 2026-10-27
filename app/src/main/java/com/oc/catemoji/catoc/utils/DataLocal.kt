package com.oc.catemoji.catoc.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.data.model.color.SelectedModel
import com.oc.catemoji.catoc.data.model.intro.IntroModel
import com.oc.catemoji.catoc.data.model.language.LanguageModel
import com.facebook.shimmer.Shimmer
import com.oc.catemoji.catoc.data.model.statistics.Statistics

object DataLocal {
    val KEY_LAST_CLICK_TIME = -101
    val shimmer = Shimmer.ColorHighlightBuilder()
        .setDuration(1200)
        .setBaseColor(0xFFCCCCCC.toInt()) // màu nền đậm hơn (xám đậm)
        .setHighlightColor(0xFFFFFFFF.toInt()) // màu highlight sáng (trắng)
        .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
        .setAutoStart(true)
        .build()
    fun getLanguageList(): ArrayList<LanguageModel> {
        return arrayListOf(
            LanguageModel("hi", "Hindi", R.drawable.ic_flag_hindi),
            LanguageModel("es", "Spanish", R.drawable.ic_flag_spanish),
            LanguageModel("fr", "French", R.drawable.ic_flag_french),
            LanguageModel("en", "English", R.drawable.ic_flag_english),
            LanguageModel("pt", "Portuguese", R.drawable.ic_flag_portugeese),
            LanguageModel("in", "Indonesian", R.drawable.ic_flag_indo),
            LanguageModel("de", "German", R.drawable.ic_flag_germani),
        )
    }

    val itemIntroList = listOf(
        IntroModel("1",R.drawable.img_intro1, R.string.title_1),
        IntroModel("2",R.drawable.img_intro2, R.string.title_2),
        IntroModel("3",R.drawable.img_intro3, R.string.title_3)
    )

    val statisticsList = arrayListOf(
        Statistics(R.drawable.total, R.string.total),
        Statistics(R.drawable.win, R.string.win),
        Statistics(R.drawable.lost, R.string.lost),
        Statistics(R.drawable.rate, R.string.rate),
        Statistics(R.drawable.streak, R.string.streak),
    )

}