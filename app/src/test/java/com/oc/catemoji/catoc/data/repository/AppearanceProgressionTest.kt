package com.oc.catemoji.catoc.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceProgressionTest {
    @Test fun allFramesBecomeAvailableByTheHighestRank() {
        assertEquals(1, maxFrameIdForRank(1))
        assertEquals(3, maxFrameIdForRank(2))
        assertEquals(15, maxFrameIdForRank(8))
        assertEquals(15, maxFrameIdForRank(Int.MAX_VALUE))
    }
}
