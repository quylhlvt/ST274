package com.oc.catemoji.catoc.data.model.custom

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

//@Parcelize
//data class CustomModel(
//    val id: String = UUID.randomUUID().toString(),
//    val avatar: String,
//    val listPath: ArrayList<BodyPartModel>,
//    val checkDataOnline: Boolean = false,
//    val updatedAt: Long = System.currentTimeMillis(),
//    val selections: ArrayList<SelectionPart> = arrayListOf(),
//    val imageSave:String= ""
//
//): Parcelable{
//    val bodyPartCount : Int by lazy { listPath.size }
//    inline  fun getBodyPart(index:Int) = listPath.getOrNull(index)
//    inline  fun isComplete() = listPath.isNotEmpty()
//    fun hasPreviewImage(): Boolean = imageSave.isNotEmpty()
//    fun getSelection(navIndex: Int): SelectionPart? {
//        return selections.getOrNull(navIndex)
//    }
//}
