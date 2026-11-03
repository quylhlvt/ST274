package com.oc.catemoji.catoc.data.model

import org.junit.Assert.*
import org.junit.Test

class DogSkinCatalogueTest {
    @Test
    fun everyDogHasTwoUniqueImagesWithTheSameType() {
        val skins = DogSkinCatalogue.create(100)
        assertEquals(30, skins.size)
        assertEquals((1..30).toSet(), skins.map { it.id }.toSet())
        val typesByFolder = List(7) { 1 } + List(5) { 2 } + List(3) { 3 }
        assertEquals(typesByFolder, skins.filter { it.imageNumber == 1 }.map { it.type })
        for (folder in 1..15) {
            val pair = skins.filter { it.folderId == folder }
            assertEquals(setOf(1, 2), pair.map { it.imageNumber }.toSet())
            pair.forEach { skin ->
                assertEquals(typesByFolder[folder - 1], skin.type)
                assertEquals("file:///android_asset/$folder/skin/${skin.imageNumber}.webp", skin.image)
                assertEquals(skin.image, DogSkinCatalogue.findById(skin.id)?.image)
            }
        }
        assertNull(DogSkinCatalogue.findById(0))
        assertNull(DogSkinCatalogue.findById(31))
    }

    @Test
    fun pricesIncreaseAndTheTwoImagesOfEveryDogStayInTheSameRow() {
        val skins = DogSkinCatalogue.create(100)
        assertEquals((0..29).map { it * 100 }, skins.map { it.price })
        assertEquals(1, skins.first().id)
        skins.chunked(2).forEachIndexed { index, pair ->
            assertEquals(listOf(index + 1, index + 1), pair.map { it.folderId })
            assertEquals(listOf(1, 2), pair.map { it.imageNumber })
        }
        assertEquals(2900, skins.last().price)
    }

    @Test
    fun previewUsesTheUnchangedStateFolderForTheSelectedImage() {
        assertEquals("file:///android_asset/1/state/puppy/1.webp", DogSkinCatalogue.findById(1)?.statePreview)
        assertEquals("file:///android_asset/1/state/adult/1.webp", DogSkinCatalogue.findById(2)?.statePreview)
        assertEquals("file:///android_asset/13/state/1. puppy/1.webp", DogSkinCatalogue.findById(25)?.statePreview)
        assertEquals("file:///android_asset/15/state/2. adult/1.webp", DogSkinCatalogue.findById(30)?.statePreview)
    }
}
