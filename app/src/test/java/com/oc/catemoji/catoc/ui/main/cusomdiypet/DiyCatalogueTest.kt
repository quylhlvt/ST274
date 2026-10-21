package com.oc.catemoji.catoc.ui.main.cusomdiypet

import com.oc.catemoji.catoc.DiyAssetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiyCatalogueTest {
    @Test fun missingManifestStillProvidesTheFullKeyboard() {
        val catalogue = CustomDiyPetViewModel.buildCatalogue("api", emptyList(), 0, 0, 0)
        assertEquals(listOf(10, 10, 9, 8), catalogue.keyboardRows.map { it.size })
        assertEquals("api/Keybroad/Keybroad/1.png", catalogue.keyboardRows.first().first())
        assertEquals(CustomDiyPetViewModel.DELETE_KEY, catalogue.keyboardRows.last().last())
        assertEquals(33, catalogue.eyes.size)
        assertEquals(34, catalogue.tags.size)
        assertEquals("api/Keybroad/Tag/34.png", catalogue.tags.last())
    }

    @Test fun shortKeyboardManifestDoesNotLoseDeleteOrCrashLaterRows() {
        val catalogue = CustomDiyPetViewModel.buildCatalogue("api", emptyList(), 2, 3, 2)
        assertEquals(listOf(3, 0, 0, 1), catalogue.keyboardRows.map { it.size })
        assertEquals(listOf(CustomDiyPetViewModel.DELETE_KEY), catalogue.keyboardRows.last())
        assertEquals(2, catalogue.eyes.size)
        assertEquals(listOf("api/Keybroad/Tag/1.png", "api/Keybroad/Tag/2.png"), catalogue.tags)
    }

    @Test fun catalogueKeepsApiCategoryInThePathAndLimitsKeysToAvailableLabels() {
        val catalogue = CustomDiyPetViewModel.buildCatalogue(
            "api", listOf(DiyAssetCategory("Cute_Cat", 2)), 1, 100, 1
        )
        assertEquals("Cute Cat", catalogue.cats.single().title)
        assertEquals("api/Cat/Cute_Cat/2.png", catalogue.cats.single().paths.last())
        assertEquals(37, catalogue.keyboardRows.sumOf { it.size })
        assertTrue(catalogue.keyboardRows.flatten().none { it.endsWith("/37.png") })
    }
}
