package com.oc.catemoji.catoc.ui.main.pause

import com.oc.catemoji.catoc.data.model.custom.BodyPartModel
import com.oc.catemoji.catoc.data.model.custom.ColorModel
import com.oc.catemoji.catoc.data.model.custom.CustomModel
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PauseChallengeSessionTest {
    @Test fun navigationUsesNumericNavOrderInsteadOfDrawingOrder() {
        val session = session(part("nav10", 0), part("nav2", 8), part("nav1", 6))
        assertEquals(listOf("nav1", "nav2", "nav10"), session.stages.map { it.nav })
        assertEquals(listOf(2, 1, 0), session.stages.map { it.partIndex })
    }

    @Test fun randomFramesIncludeColorsAndLayersButNeverSpecialEntries() {
        val session = session(BodyPartModel(nav = "nav1", listPath = arrayListOf(
            ColorModel(listPath = arrayListOf("none", "dice", "red1", "red2")),
            ColorModel(listPath = arrayListOf("none", "blue1"))
        )))
        assertEquals(setOf("red1", "red2", "blue1"), session.currentStage!!.choices.map { it.path }.toSet())
        assertEquals(setOf(0, 1), session.currentStage!!.choices.map { it.colorIndex }.toSet())
        val random = Random(3)
        repeat(20) {
            val previous = session.selectedChoices[0]
            val next = session.randomChoice(random)!!
            assertNotEquals(previous, next)
            session.show(next)
        }
    }

    @Test fun realCatalogueUrlsUseNavigationMetadataRatherThanTemplateNumbers() {
        val session = session(
            part("https://cdn229.test/data22/2-3/nav.png", 3),
            part("https://cdn229.test/data22/3-1/nav.png", 1),
            part("https://cdn229.test/data22/1-2/nav.png", 2)
        )
        assertEquals(listOf(1, 2, 0), session.stages.map { it.partIndex })
    }

    @Test fun cannotFreezeAFrameThatHasNotBeenDisplayed() {
        val session = session(part("nav1", 0), part("nav2", 1))
        assertFalse(session.advance())
        assertEquals(0, session.stageIndex)
    }

    @Test fun freezingKeepsPreviousLayersAndFinishesOnlyAfterLastNav() {
        val session = session(part("nav1", 0), part("nav2", 1))
        val first = session.randomChoice()!!
        session.show(first)
        assertTrue(session.advance())
        assertFalse(session.isComplete)
        session.show(session.randomChoice()!!)
        assertEquals(first, session.selectedChoices[0])
        assertTrue(session.advance())
        assertTrue(session.isComplete)
        assertEquals(2, session.selectedChoices.size)
        assertFalse(session.advance())
    }

    @Test fun skipRemovesOnlyCurrentLayerAndEmptyPartsAreIgnored() {
        val session = session(part("nav1", 0), BodyPartModel(nav = "nav2"), part("nav3", 2))
        session.show(session.randomChoice()!!)
        session.advance()
        session.show(session.randomChoice()!!)
        session.advance(skip = true)
        assertTrue(session.isComplete)
        assertEquals(setOf(0), session.selectedChoices.keys)
    }

    @Test fun oneVariantStillWorksAcrossRepeatedTicks() {
        val session = session(part("nav1", 0))
        val choice = session.randomChoice()!!
        session.show(choice)
        assertEquals(choice, session.randomChoice())
        assertTrue(session.advance())
    }

    private fun session(vararg parts: BodyPartModel) =
        PauseChallengeSession(CustomModel(listPath = ArrayList(parts.toList())))

    private fun part(nav: String, position: Int) = BodyPartModel(
        nav = nav, position = position, zIndex = position,
        listPath = arrayListOf(ColorModel(listPath = arrayListOf("none", "dice", "$nav.png")))
    )
}
