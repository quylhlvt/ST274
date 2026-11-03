package com.oc.catemoji.catoc.data.model

data class DogSkinOption(val folderId: Int, val imageNumber: Int, val price: Int) {
    // Identity is tied to the folder/image pair, so sorting by type cannot change saved selections.
    val id: Int get() = (folderId - 1) * 2 + imageNumber
    val type: Int get() = when {
        folderId >= 13 -> 3
        folderId >= 8 -> 2
        else -> 1
    }
    val winsToMaxLevel: Int get() = DogSkinCatalogue.FIRST_SKIN_WIN_TARGET + id - 1
    val image: String get() = "file:///android_asset/$folderId/skin/$imageNumber.webp"
    val statePreview: String get() = "$stateDirectory/1.webp"
    val statePlaying: String get() = "$stateDirectory/2.webp"

    fun atProgress(progress: Int): DogSkinOption =
        copy(imageNumber = if (progress >= 100) 2 else 1)

    private val stateDirectory: String get() {
        val stage = if (imageNumber == 1) "puppy" else "adult"
        val directory = if (folderId <= 2) stage else "$imageNumber. $stage"
        return "file:///android_asset/$folderId/state/$directory"
    }
}

object DogSkinCatalogue {
    const val FIRST_SKIN_WIN_TARGET = 3
    const val FOLDER_COUNT = 15
    const val SKIN_COUNT = FOLDER_COUNT * 2

    fun create(priceStep: Int): List<DogSkinOption> {
        require(priceStep in 1..Int.MAX_VALUE / (SKIN_COUNT - 1))
        return (1..FOLDER_COUNT).flatMap { folder ->
            (1..2).map { number ->
                val priceIndex = (folder - 1) * 2 + number - 1
                DogSkinOption(folder, number, priceIndex * priceStep)
            }
        }
    }

    fun findById(id: Int): DogSkinOption? = id.takeIf { it in 1..SKIN_COUNT }?.let {
        DogSkinOption(folderId = (it - 1) / 2 + 1, imageNumber = (it - 1) % 2 + 1, price = 0)
    }
}
