package com.oc.catemoji.catoc.data.model.custom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomModelEditValidationTest {
    private val template = CustomModel(
        id = "template_data_2",
        listPath = arrayListOf(
            part("tail", 10, listOf("tail.png")),
            part("body", 0, listOf("none", "dice", "body.png"), listOf("body-blue.png"))
        )
    )
    private val saved = template.copy(
        id = "customized",
        templateId = template.id,
        selections = arrayListOf(SelectionIndex(0, 0, 2), SelectionIndex(1, 0, 0))
    )

    @Test
    fun editUsesTheSameZIndexOrderAsCustomize() {
        assertTrue(saved.hasValidSelectionsFor(template))
        assertEquals(listOf("tail", "body"), template.listPath.map { it.nav })
    }

    @Test
    fun removedPathIsRejectedEvenWhenSavedCharacterStillContainsIt() {
        val current = template.copy(
            listPath = arrayListOf(part("tail", 10, listOf("tail.png")), part("body", 0, listOf("none")))
        )
        assertTrue(saved.hasValidSelectionsFor(template))
        assertFalse(saved.hasValidSelectionsFor(current))
    }

    @Test
    fun removedColorIsRejected() {
        val blue = saved.copy(selections = arrayListOf(SelectionIndex(0, 1, 0)))
        val current = template.copy(listPath = arrayListOf(part("body", 0, listOf("body.png"))))
        assertTrue(blue.hasValidSelectionsFor(template))
        assertFalse(blue.hasValidSelectionsFor(current))
    }

    @Test
    fun negativeAndOutOfRangeIndicesAreRejected() {
        for (selection in listOf(
            SelectionIndex(0, -1, 0),
            SelectionIndex(0, 2, 0),
            SelectionIndex(0, 0, -1),
            SelectionIndex(0, 0, 3)
        )) {
            assertFalse(selection.toString(), saved.copy(selections = arrayListOf(selection)).hasValidSelectionsFor(template))
        }
    }

    @Test
    fun removedBodyPartIsRejected() {
        val current = template.copy(listPath = arrayListOf(template.listPath[1]))
        assertFalse(saved.hasValidSelectionsFor(current))
    }

    @Test
    fun existingNoneAndDicePathsRemainValid() {
        for (pathIndex in listOf(0, 1)) {
            val character = saved.copy(selections = arrayListOf(SelectionIndex(0, 0, pathIndex)))
            assertTrue(character.hasValidSelectionsFor(template))
        }
    }

    @Test
    fun legacyPartialSelectionsCanUseCustomizeDefaultsForRemainingParts() {
        assertTrue(saved.copy(selections = arrayListOf(SelectionIndex(0, 0, 2))).hasValidSelectionsFor(template))
        assertTrue(saved.copy(selections = arrayListOf()).hasValidSelectionsFor(template))
    }

    private fun part(nav: String, zIndex: Int, vararg paths: List<String>) = BodyPartModel(
        nav = nav,
        zIndex = zIndex,
        listPath = ArrayList(paths.map { ColorModel(listPath = ArrayList(it)) })
    )
}
