package com.oc.catemoji.catoc.ui.main.dogSkin

import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.model.DogSkinOption
import com.oc.catemoji.catoc.data.model.DogSkinCatalogue
import org.junit.Assert.*
import org.junit.Test

class DogSkinItemTest {
    private val skin = DogSkinOption(folderId = 1, imageNumber = 2, price = 100)

    @Test
    fun enoughCoinMakesPurchasePossibleButDoesNotUnlockWithoutBuying() {
        assertTrue(DogSkinItem.from(skin, null, 99, null).locked)
        assertTrue(DogSkinItem.from(skin, null, 100, null).locked)
        assertTrue(DogSkinItem.from(skin, null, 101, null).locked)
        assertFalse(DogSkinItem.from(skin, null, 99, null).canAfford)
        assertTrue(DogSkinItem.from(skin, null, 100, null).canAfford)
        assertTrue(DogSkinItem.from(skin, DogEntity(2, unlock = false), 0, null).locked)
    }

    @Test
    fun ownedSkinStaysAvailableWhenBalanceDrops() {
        val dog = DogEntity(2, stepRank = 1, unlock = true, selected = true, matchesPlayed = 20, matchesWon = 9)
        val item = DogSkinItem.from(skin, dog, 0, 2)
        assertFalse(item.locked)
        assertTrue(item.selected)
        assertEquals(100, item.progress)
        assertEquals(skin.image, item.image)
        assertFalse(DogSkinItem.from(skin, dog, 0, 1).selected)
    }

    @Test
    fun affordableSkinDoesNotBecomeSelectedWithoutSavedSelection() {
        assertFalse(DogSkinItem.from(skin, null, 100, 2).selected)
        assertFalse(DogSkinItem.from(skin, DogEntity(2, selected = true), 100, 2).selected)
        assertTrue(DogSkinItem.from(skin, null, 100, 2).locked)
    }

    @Test
    fun progressComesFromWinsAndShowsThePuppyBelowMaxLevel() {
        assertEquals(0, DogSkinItem.from(skin, DogEntity(2, stepRank = 3, progress = 100), 100, null).progress)
        val item = DogSkinItem.from(skin,
            DogEntity(2, stepRank = 3, progress = 10, matchesPlayed = 3, matchesWon = 2), 100, null)
        assertEquals(50, item.progress)
        assertEquals(3L, item.matchesPlayed)
        assertEquals(2L, item.matchesWon)
        assertEquals("file:///android_asset/1/skin/1.webp", item.image)
        assertEquals(1, item.skin.type)
    }

    @Test
    fun everySkinNeedsOneMoreWinThanThePreviousSkinAndReachesMaxOnlyAtItsTarget() {
        DogSkinCatalogue.create(100).filter { it.imageNumber == 1 }.forEach { option ->
            assertEquals(option.id + 2, option.winsToMaxLevel)
            fun item(wins: Long, played: Long = 100) = DogSkinItem.from(option,
                DogEntity(option.id, unlock = true, matchesPlayed = played, matchesWon = wins), 0, null)
            val target = option.winsToMaxLevel.toLong()
            assertEquals(0, item(0).progress)
            assertFalse(item(target - 1).isMaxLevel)
            assertTrue(item(target - 1).progress < 100)
            assertEquals("file:///android_asset/${option.folderId}/skin/1.webp", item(target - 1).image)
            assertTrue(item(target).isMaxLevel)
            assertEquals(100, item(target).progress)
            assertEquals("file:///android_asset/${option.folderId}/skin/2.webp", item(target).image)
            assertEquals(100, item(target + 10).progress)
            assertEquals(item(1, 1).progress, item(1, 100).progress)
        }
    }

    @Test
    fun combinedCardPreservesLegacyAdultOwnershipSelectionAndWins() {
        val puppy = checkNotNull(DogSkinCatalogue.findById(3))
        val adult = DogEntity(4, unlock = true, selected = true, matchesPlayed = 5, matchesWon = 3)

        val adultOnly = DogSkinItem.from(puppy, null, 0, adult.iddog, adultDog = adult)
        assertFalse(adultOnly.locked)
        assertTrue(adultOnly.selected)
        assertEquals(4, adultOnly.equipSkinId)
        assertEquals(60, adultOnly.progress)
        assertEquals("file:///android_asset/2/skin/1.webp", adultOnly.image)

        val puppyDog = DogEntity(3, unlock = true, matchesPlayed = 4, matchesWon = 2)
        val combined = DogSkinItem.from(puppy, puppyDog, 0, adult.iddog, adultDog = adult)
        assertTrue(combined.selected)
        assertEquals(9L, combined.matchesPlayed)
        assertEquals(5L, combined.matchesWon)
        assertEquals(100, combined.progress)
        assertEquals("file:///android_asset/2/skin/2.webp", combined.image)
    }
}
