package com.oc.catemoji.catoc.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.model.addcharacter.SelectedAddModel
import com.oc.catemoji.catoc.data.model.color.SelectedModel
import com.oc.catemoji.catoc.data.model.intro.IntroModel
import com.oc.catemoji.catoc.data.model.language.LanguageModel
import com.facebook.shimmer.Shimmer

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

    fun getBackgroundColorDefault(context: Context): ArrayList<SelectedAddModel> {
        return arrayListOf(
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_1)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_2)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_3)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_4)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_5)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_6)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_7)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_8)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_9)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_10)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_11)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_12)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_13)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_14)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_15)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_16)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_17)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_18)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_19)),
        )
    }

    val bottomNavigationNotSelect = arrayListOf(
        R.drawable.ic_background,
        R.drawable.ic_sticker,
        R.drawable.ic_speech,
        R.drawable.ic_text,
    )

    val bottomNavigationSelected = arrayListOf(
        R.drawable.ic_background_selected,
        R.drawable.ic_sticker_selected,
        R.drawable.ic_speech_selected,
        R.drawable.ic_text_selected,
    )

    fun getTextFontDefault(): ArrayList<SelectedAddModel> {
        return arrayListOf(
            SelectedAddModel(color = R.font.itim_regular),
            SelectedAddModel(color = R.font.italianno_regular),
            SelectedAddModel(color = R.font.kranky_regular),
            SelectedAddModel(color = R.font.damion_regular),
            SelectedAddModel(color = R.font.dynalight_regular),
            SelectedAddModel(color = R.font.js_math_cmmi),
            SelectedAddModel(color = R.font.mystery_quest_regular),
            SelectedAddModel(color = R.font.bubblegum_sans_regular),
            SelectedAddModel(color = R.font.cherry_bomb_one_regular),
            SelectedAddModel(color = R.font.cutive_mono_regular),
            SelectedAddModel(color = R.font.croissant_one_regular)
        )
    }

    fun getTextColorDefault(context: Context): ArrayList<SelectedAddModel> {
        return arrayListOf(
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_9)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.black)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.white)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_19)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_2)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_3)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_4)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_5)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_6)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_7)),
            SelectedAddModel(color = ContextCompat.getColor(context, R.color.color_8))
        )
    }
}