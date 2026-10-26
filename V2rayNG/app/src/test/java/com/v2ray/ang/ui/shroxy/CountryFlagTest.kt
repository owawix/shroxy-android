package com.v2ray.ang.ui.shroxy

import com.v2ray.ang.R
import com.v2ray.ang.ui.shroxy.components.CountryFlags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CountryFlagTest {

    @Test
    fun remarkLeadingWithRegionalIndicatorPairResolvesToEmoji() {
        assertEquals("🇸🇪", CountryFlags.extractEmojiFlag("🇸🇪 Sweden"))
        assertEquals("🇺🇸", CountryFlags.extractEmojiFlag("🇺🇸 US West"))
    }

    @Test
    fun remarkWithoutEmojiReturnsNull() {
        assertNull(CountryFlags.extractEmojiFlag("Швеция"))
        assertNull(CountryFlags.extractEmojiFlag(""))
    }

    @Test
    fun englishCountryNameResolvesToVectorFlag() {
        assertEquals(R.drawable.shroxy_flag_se, CountryFlags.resolveFlagRes("Sweden"))
        assertEquals(R.drawable.shroxy_flag_nl, CountryFlags.resolveFlagRes("Netherlands"))
    }

    @Test
    fun russianCountryNameResolvesToVectorFlag() {
        assertEquals(R.drawable.shroxy_flag_se, CountryFlags.resolveFlagRes("Швеция"))
        assertEquals(R.drawable.shroxy_flag_ru, CountryFlags.resolveFlagRes("Россия"))
    }

    @Test
    fun isoCodeResolvesToVectorFlagCaseInsensitively() {
        assertEquals(R.drawable.shroxy_flag_se, CountryFlags.resolveFlagRes("SE"))
        assertEquals(R.drawable.shroxy_flag_se, CountryFlags.resolveFlagRes(" se "))
        assertEquals(R.drawable.shroxy_flag_gb, CountryFlags.resolveFlagRes("uk"))
    }

    @Test
    fun unknownOrMissingCountryResolvesToNull() {
        assertNull(CountryFlags.resolveFlagRes("Atlantis"))
        assertNull(CountryFlags.resolveFlagRes(null))
        assertNull(CountryFlags.resolveFlagRes(""))
    }

    @Test
    fun stripLeadingFlagRemovesEmojiAndSeparatorFromTitle() {
        val result = CountryFlags.stripLeadingFlag("🇳🇱 Нидерланды • Hysteria")
        assertEquals("🇳🇱", result.emoji)
        assertEquals("Нидерланды • Hysteria", result.cleanTitle)
    }

    @Test
    fun stripLeadingFlagHandlesSingleSpaceSeparator() {
        val result = CountryFlags.stripLeadingFlag("🇸🇪 Sweden")
        assertEquals("🇸🇪", result.emoji)
        assertEquals("Sweden", result.cleanTitle)
    }

    @Test
    fun stripLeadingFlagWithoutEmojiKeepsTitleUnchanged() {
        val result = CountryFlags.stripLeadingFlag("Швеция")
        assertNull(result.emoji)
        assertEquals("Швеция", result.cleanTitle)
    }

    @Test
    fun stripLeadingFlagWithInvalidPairKeepsTitleUnchanged() {
        // A single regional indicator (not a pair) and an unrelated emoji must not strip.
        val single = CountryFlags.stripLeadingFlag("🇺 Sweden")
        assertNull(single.emoji)
        assertEquals("🇺 Sweden", single.cleanTitle)
        val unrelated = CountryFlags.stripLeadingFlag("⚡ Fast server")
        assertNull(unrelated.emoji)
        assertEquals("⚡ Fast server", unrelated.cleanTitle)
    }

    @Test
    fun stripLeadingFlagWithOnlyEmojiKeepsTitleUnchanged() {
        val result = CountryFlags.stripLeadingFlag("🇺🇸")
        assertNull(result.emoji)
        assertEquals("🇺🇸", result.cleanTitle)
    }
}
