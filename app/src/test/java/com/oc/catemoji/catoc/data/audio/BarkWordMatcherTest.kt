package com.oc.catemoji.catoc.data.audio

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class BarkWordMatcherTest {
    @Test
    fun countsAccentVariantsUsingOriginalAccentInsensitiveMatching() {
        assertEquals(6, BarkWordMatcher.countBarks("GÂU gầu gấu gao gô gõ"))
        assertEquals(3, BarkWordMatcher.countBarks("gâu gâu go"))
    }

    @Test
    fun countsMatchingTokensWithinLongerTextButNeverSubstrings() {
        assertEquals(3, BarkWordMatcher.countBarks("gâu,gâu!\nwoof?"))
        assertEquals(1, BarkWordMatcher.countBarks("tôi nói gâu hôm nay"))
        assertEquals(0, BarkWordMatcher.countBarks("gaucho google suaabc [unk]"))
        assertFalse(BarkWordMatcher.isBark(""))
        assertFalse(BarkWordMatcher.isBark("xin chào"))
        assertTrue(BarkWordMatcher.isBark("gâu!"))
    }

    @Test
    fun normalizesVietnameseDAndDecomposedUnicodeIndependentOfLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("di gau", BarkWordMatcher.normalize("ĐI GÂU"))
            assertEquals(2, BarkWordMatcher.countBarks("ga\u0302u ga\u0302\u0300u"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun explicitlyRemovedWordsAndAccentVariantsDoNotCount() {
        listOf("sủa", "sửa", "sữa", "sua", "yap", "yip", "bau", "bầu", "hong", "Hồng", "au", "âu", "guk").forEach {
            assertEquals(it, 0, BarkWordMatcher.countBarks(it))
        }
        assertEquals(2, BarkWordMatcher.countBarks("gâu sủa yap yip bau hong au guk woof"))
    }

    @Test
    fun countsInternationalWordsAndRepetitionsFromTheOriginalSharedList() {
        assertEquals(5, BarkWordMatcher.countBarks("woof ouaf wuff guau wan"))
        listOf("gaugau", "wanwan", "woofwoof", "汪汪", "ワンワン", "わんわん", "멍멍", "왈왈", "โฮ่งๆ").forEach {
            assertEquals(it, 2, BarkWordMatcher.countBarks(it))
        }
        assertEquals(2, BarkWordMatcher.countBarks("гав-гав"))
        assertEquals(2, BarkWordMatcher.countBarks("भौं भौं"))
        assertEquals(0, BarkWordMatcher.countBarks("भ"))
    }
}
