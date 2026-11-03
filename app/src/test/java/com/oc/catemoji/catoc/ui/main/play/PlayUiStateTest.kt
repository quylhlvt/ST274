package com.oc.catemoji.catoc.ui.main.play

import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.datalocal.room.entity.BattleResultEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.model.DogSkinCatalogue
import org.junit.Assert.*
import org.junit.Test

class PlayUiStateTest {
    private fun receipt(won: Boolean) = BattleResultEntity(
        "match", 1L, won, if (won) 100 else 5, if (won) 30 else -10, 1, 2, "1,6",
    )

    @Test fun defeatUsesWhiteTitleAndLostArtworkWhileVictoryKeepsGold() {
        val lost = PlayRewardUiState.from(receipt(false))
        assertEquals(R.string.profile_defeat, lost.title)
        assertEquals(R.color.white, lost.gradientStart)
        assertEquals(R.color.white, lost.gradientEnd)
        assertEquals(R.drawable.img_lost_play, lost.image)
        assertEquals(5, lost.bones)
        assertFalse(lost.won)
        assertEquals(-10, lost.rankPoints)
        val won = PlayRewardUiState.from(receipt(true))
        assertEquals(R.string.victory, won.title)
        assertEquals(R.color._FFEBA7, won.gradientStart)
        assertEquals(R.color._FFCB14, won.gradientEnd)
        assertEquals(R.drawable.img_win_play, won.image)
        assertEquals(listOf(1, 6), won.badges.map { it.id })
        assertTrue(won.won)
        assertEquals(30, won.rankPoints)
    }

    @Test fun claimedBonusDoublesDisplayedBonesAndDisablesAnotherClaim() {
        val original = PlayRewardUiState.from(receipt(true))
        val claimed = PlayRewardUiState.from(receipt(true).copy(bonusClaimed = true))
        assertEquals(original.bones * 2, claimed.bones)
        assertEquals(original.rankPoints, claimed.rankPoints)
        assertEquals(R.string.play_reward_claimed, claimed.bonusLabel)
        assertTrue(PlayResultUiState(reward = original).canClaimBonus)
        assertFalse(PlayResultUiState(reward = claimed).canClaimBonus)
    }

    @Test fun savingOrAdsAndSaveFailuresBlockLeavingAndClaiming() {
        val ready = PlayResultUiState(BattlePhase.REWARD, PlayRewardUiState.from(receipt(false)))
        assertTrue(ready.canLeave)
        listOf(ready.copy(busy = true), ready.copy(saveFailed = true)).forEach {
            assertFalse(it.canLeave)
            assertFalse(it.canClaimBonus)
        }
        assertFalse(PlayResultUiState().canClaimBonus)
    }

    @Test fun invalidRanksAreClampedBeforeTheActivityIndexesDrawables() {
        val reward = PlayRewardUiState.from(receipt(false).copy(previousRank = Int.MIN_VALUE, nextRank = Int.MAX_VALUE))
        assertEquals(0, reward.previousRankIconIndex)
        assertEquals(7, reward.nextRankIconIndex)
        val player = PlayPlayerUiState(UserEntity(rank = Int.MAX_VALUE))
        assertEquals(7, player.rankIconIndex)
        assertEquals(300L, player.tapOpponentIntervalMs)
    }

    @Test fun characterImageUsesTheEquippedSkinAndMatchCanKeepItsStartingSkin() {
        val profile = UserEntity(iddog = 3, imageAvatar = "file:///android_asset/9/avatar.webp")
        assertEquals("file:///android_asset/2/state/puppy/1.webp", PlayPlayerUiState(profile).characterImage)
        assertEquals("file:///android_asset/1/state/puppy/1.webp", PlayPlayerUiState(profile, skinId = 2).characterImage)
        val grown = PlayPlayerUiState(profile, skinId = 1, characterSkinId = 2)
        assertEquals("file:///android_asset/1/state/adult/1.webp", grown.characterImage)
    }

    @Test fun playerUsesImageOneBeforeStartAndImageTwoWhilePlayingForEverySkin() {
        DogSkinCatalogue.create(100).forEach { skin ->
            val stage = if (skin.imageNumber == 1) "puppy" else "adult"
            val directory = if (skin.folderId <= 2) stage else "${skin.imageNumber}. $stage"
            val path = "file:///android_asset/${skin.folderId}/state/$directory"
            val ready = PlayPlayerUiState(
                UserEntity(iddog = skin.id),
                skinProgressPercent = if (skin.imageNumber == 2) 100 else 99,
            )
            assertEquals("$path/1.webp", ready.characterImage)
            assertEquals("$path/2.webp", ready.copy(useActiveCharacter = true).characterImage)
        }
    }

    @Test fun opponentUsesAdultCharacterStatesWhileKeepingPortraitForVsAndHistory() {
        (2..15).forEach { folderId ->
            val opponent = PlayOpponentUiState(folderId)
            val adult = checkNotNull(DogSkinCatalogue.findById(folderId * 2))
            assertEquals("file:///android_asset/$folderId/avatar.webp", opponent.portraitImage)
            assertEquals(adult.statePreview, opponent.characterImage(false))
            assertEquals(adult.statePlaying, opponent.characterImage(true))
        }
    }

    @Test fun unknownSkinFallsBackToProfileAvatarForBothStates() {
        val ready = PlayPlayerUiState(UserEntity(iddog = 0, imageAvatar = "saved-avatar"))
        assertEquals("saved-avatar", ready.characterImage)
        assertEquals("saved-avatar", ready.copy(useActiveCharacter = true).characterImage)
    }

    @Test fun battleCadenceUsesTheMatchSkinAndItsProgressInsteadOfProfileRank() {
        val beginner = PlayPlayerUiState(UserEntity(rank = 8), skinId = 1)
        val trained = beginner.copy(skinProgressPercent = 100)
        val laterSkin = beginner.copy(skinId = 30)
        assertTrue(trained.tapOpponentIntervalMs < beginner.tapOpponentIntervalMs)
        assertTrue(trained.microphoneOpponentIntervalMs < beginner.microphoneOpponentIntervalMs)
        assertTrue(laterSkin.tapOpponentIntervalMs < beginner.tapOpponentIntervalMs)
        assertTrue(laterSkin.microphoneOpponentIntervalMs < beginner.microphoneOpponentIntervalMs)
        assertEquals(beginner.tapOpponentIntervalMs, beginner.copy(profile = UserEntity(rank = 1)).tapOpponentIntervalMs)
    }
}
