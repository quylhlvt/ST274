package com.oc.catemoji.catoc.ui.main.play

import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.datalocal.room.entity.BattleResultEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import org.junit.Assert.*
import org.junit.Test

class PlayUiStateTest {
    private fun receipt(won: Boolean) = BattleResultEntity(
        "match", 1L, won, if (won) 100 else 25, if (won) 30 else 10, 1, 2, "1,6",
    )

    @Test fun defeatUsesWhiteTitleAndLostArtworkWhileVictoryKeepsGold() {
        val lost = PlayRewardUiState.from(receipt(false))
        assertEquals(R.string.profile_defeat, lost.title)
        assertEquals(R.color.white, lost.gradientStart)
        assertEquals(R.color.white, lost.gradientEnd)
        assertEquals(R.drawable.img_lost_play, lost.image)
        assertEquals(25, lost.bones)
        val won = PlayRewardUiState.from(receipt(true))
        assertEquals(R.string.victory, won.title)
        assertEquals(R.color._FFEBA7, won.gradientStart)
        assertEquals(R.color._FFCB14, won.gradientEnd)
        assertEquals(R.drawable.img_win_play, won.image)
        assertEquals(listOf(1, 6), won.badges.map { it.id })
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

    @Test funSavingOrAdsAndSaveFailuresBlockLeavingAndClaiming() {
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
        assertEquals(100L, player.opponentIntervalMs)
    }
}
