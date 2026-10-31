package com.oc.catemoji.catoc.ui.main.play

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.data.datalocal.room.entity.AchievementEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.BattleResultEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.model.BattleRules

data class PlayBattleUiState(
    /** 3, 2, 1, then 0 for START; null hides the countdown. */
    val countdownNumber: Int? = null,
    val canPlay: Boolean = false,
    val canTap: Boolean = false,
    val runTapOpponent: Boolean = false,
)

data class PlayPlayerUiState(val profile: UserEntity) {
    val rankIconIndex: Int = rankIconIndex(profile.rank)
    val opponentIntervalMs: Long = TapBattleDifficulty.opponentIntervalMs(profile.rank)
}

data class PlayRewardUiState(
    val badges: List<AchievementEntity>,
    val previousRank: Int,
    val nextRank: Int,
    val previousRankIconIndex: Int,
    val nextRankIconIndex: Int,
    @StringRes val title: Int,
    @ColorRes val gradientStart: Int,
    @ColorRes val gradientEnd: Int,
    @DrawableRes val image: Int,
    @DrawableRes val imageBgTitel: Int,
    val bones: Int,
    val rankPoints: Int,
    val bonusClaimed: Boolean,
    @StringRes val bonusLabel: Int,
) {
    companion object {
        fun from(result: BattleResultEntity) = PlayRewardUiState(
            badges = result.badges(),
            previousRank = result.previousRank,
            nextRank = result.nextRank,
            previousRankIconIndex = rankIconIndex(result.previousRank),
            nextRankIconIndex = rankIconIndex(result.nextRank),
            title = if (result.won) R.string.victory else R.string.profile_defeat,
            gradientStart = if (result.won) R.color._FFEBA7 else R.color.white,
            gradientEnd = if (result.won) R.color._FFCB14 else R.color.white,
            image = if (result.won) R.drawable.img_win_play else R.drawable.img_lost_play,
            imageBgTitel = if (result.won) R.drawable.bg_title_win else R.drawable.bg_title_lost,
            bones = result.bones * if (result.bonusClaimed) 2 else 1,
            rankPoints = result.rankPoints,
            bonusClaimed = result.bonusClaimed,
            bonusLabel = if (result.bonusClaimed) R.string.play_reward_claimed else R.string.play_reward_double,
        )
    }
}

data class PlayResultUiState(
    val phase: BattlePhase = BattlePhase.PLAYING,
    val reward: PlayRewardUiState? = null,
    val busy: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val isPlaying: Boolean get() = phase == BattlePhase.PLAYING
    val showGameOver: Boolean get() = phase == BattlePhase.GAME_OVER
    val showBadges: Boolean get() = phase == BattlePhase.BADGES
    val showRank: Boolean get() = phase == BattlePhase.RANK
    val showReward: Boolean get() = phase == BattlePhase.REWARD
    val showResults: Boolean get() = showBadges || showRank || showReward
    val canLeave: Boolean get() = !busy && !saveFailed
    val canClaimBonus: Boolean get() = canLeave && reward?.bonusClaimed == false
}

private fun rankIconIndex(rank: Int): Int = rank.coerceIn(1, BattleRules.MAX_RANK) - 1
