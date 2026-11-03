package com.oc.catemoji.catoc.data.repository

import com.oc.catemoji.catoc.data.model.BattleRules
import com.oc.catemoji.catoc.data.model.DogSkinCatalogue

/** Frame 1 starts unlocked; each of the seven rank promotions grants two more. */
internal fun maxFrameIdForRank(rank: Int): Int =
    (rank.coerceIn(1, BattleRules.MAX_RANK) * 2 - 1).coerceAtMost(DogSkinCatalogue.FOLDER_COUNT)
