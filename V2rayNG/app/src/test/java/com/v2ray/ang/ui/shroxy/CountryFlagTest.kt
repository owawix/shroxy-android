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
}
