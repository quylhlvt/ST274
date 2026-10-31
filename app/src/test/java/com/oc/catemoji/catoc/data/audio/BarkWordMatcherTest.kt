package com.oc.catemoji.catoc.data.audio

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class BarkWordMatcherTest {
    @Test fun acceptsGauAndGoIncludingCanonicalUnicodeAndPunctuation() {
        assertEquals(5, BarkWordMatcher.countBarks("GÂU, gâu! ga\u0302u? go gô"))
        assertEquals(1, BarkWordMatcher.countBarks("tôi nói gâu hôm nay"))
        assertTrue(BarkWordMatcher.isBarkWord("GÂU"))
        assertFalse(BarkWordMatcher.isBarkWord("gâu gâu"))
    }

    @Test fun rejectsOtherWordsAndJoinedForms() {
        listOf("mõ", "vàng", "giàng", "gấu", "gầu", "gạo", "gao", "gõ", "gỗ",
            "gaugau", "gâugâu", "woof", "sủa", "sửa", "sữa", "[unk]",
            "google", "gaucho", "gâu123", "123gâu").forEach {
            assertEquals(it, 0, BarkWordMatcher.countBarks(it))
        }
        assertEquals(0, BarkWordMatcher.countBarks("mõ vàng giàng gấu gầu gạo"))
    }

    @Test fun normalizationPreservesToneMarksIndependentOfTheDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("đi gâu", BarkWordMatcher.normalize("ĐI GA\u0302U"))
            assertEquals(1, BarkWordMatcher.countBarks("ga\u0302u ga\u0302\u0300u"))
        } finally { Locale.setDefault(original) }
    }
}
