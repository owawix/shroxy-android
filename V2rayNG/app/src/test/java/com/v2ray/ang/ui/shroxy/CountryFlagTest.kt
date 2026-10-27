package com.v2ray.ang.ui.shroxy

import com.v2ray.ang.ui.shroxy.components.CountryFlags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CountryFlagTest {

    @BeforeEach
    fun clearDrawableCache() {
        CountryFlags.clearDrawableCacheForTest()
    }

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
    fun emojiFlagConvertsToIsoCode() {
        assertEquals("us", CountryFlags.emojiToCode("🇺🇸"))
        assertEquals("se", CountryFlags.emojiToCode("🇸🇪"))
    }

    @Test
    fun emojiInRemarkResolvesToCountryCode() {
        assertEquals("us", CountryFlags.resolveCode(country = null, remark = "🇺🇸 US West"))
        assertEquals("se", CountryFlags.resolveCode(country = null, remark = "🇸🇪 Sweden"))
    }

    @Test
    fun englishCountryNameResolvesToCode() {
        assertEquals("se", CountryFlags.resolveCode(country = "Sweden", remark = null))
        assertEquals("nl", CountryFlags.resolveCode(country = "Netherlands", remark = null))
    }

    @Test
    fun russianCountryNameResolvesToCode() {
        assertEquals("se", CountryFlags.resolveCode(country = "Швеция", remark = null))
        assertEquals("ru", CountryFlags.resolveCode(country = "Россия", remark = null))
        assertEquals("us", CountryFlags.resolveCode(country = "США", remark = null))
        assertEquals("gb", CountryFlags.resolveCode(country = "Великобритания", remark = null))
    }

    @Test
    fun nameAliasesResolveToCode() {
        assertEquals("us", CountryFlags.resolveCode(country = "America", remark = null))
        assertEquals("gb", CountryFlags.resolveCode(country = "UK", remark = null))
        assertEquals("gb", CountryFlags.resolveCode(country = "Great Britain", remark = null))
        assertEquals("ae", CountryFlags.resolveCode(country = "ОАЭ", remark = null))
    }

    @Test
    fun isoCodeResolvesCaseInsensitively() {
        assertEquals("se", CountryFlags.resolveCode(country = "SE", remark = null))
        assertEquals("se", CountryFlags.resolveCode(country = " se ", remark = null))
    }

    @Test
    fun codeTokenInRemarkResolvesWithWordBoundary() {
        assertEquals("us", CountryFlags.resolveCode(country = null, remark = "US West"))
        assertEquals("us", CountryFlags.resolveCode(country = null, remark = "us-west-3"))
    }

    @Test
    fun codeTokenInRemarkIgnoresNonWordTokens() {
        // "Fast"/"server" contain no standalone two-letter token; "Atlantis" neither.
        assertNull(CountryFlags.resolveCode(country = null, remark = "Fast server"))
        assertNull(CountryFlags.resolveCode(country = null, remark = "Atlantis"))
    }

    @Test
    fun codeTokenDoesNotMatchWhenGluedToDigitsOrUnderscore() {
        assertNull(CountryFlags.resolveCode(country = null, remark = "v2at5"))
        assertNull(CountryFlags.resolveCode(country = null, remark = "node_at6"))
    }

    @Test
    fun codeTokenMatchesAcrossDigitSeparatedBoundary() {
        assertEquals("at", CountryFlags.resolveCode(country = null, remark = "at 1"))
        assertEquals("at", CountryFlags.resolveCode(country = null, remark = "AT-1"))
    }

    @Test
    fun subdivisionTokenMapsToUnderscoredCode() {
        assertEquals("gb_eng", CountryFlags.resolveCode(country = null, remark = "gb-eng"))
        assertEquals("gb_eng", CountryFlags.resolveCode(country = "GB-ENG", remark = null))
        assertEquals("shroxy_flag_gb_eng", CountryFlags.flagDrawableName("gb-eng"))
        assertEquals("shroxy_flag_gb_eng", CountryFlags.flagDrawableName("gb_eng"))
    }

    @Test
    fun unknownSubdivisionResolvesToPlaceholder() {
        // "ab-cde" matches the subdivision shape but is not a bundled code, so resolution
        // falls through: no standalone token either — the UI renders the placeholder.
        assertNull(CountryFlags.resolveCode(country = null, remark = "ab-cde"))
        // A known subdivision still resolves and names its bundled vector.
        assertEquals("gb_eng", CountryFlags.resolveCode(country = null, remark = "gb-eng"))
        assertEquals(
            "shroxy_flag_gb_eng",
            CountryFlags.flagDrawableName(CountryFlags.resolveCode(country = null, remark = "gb-eng")!!),
        )
    }

    @Test
    fun unknownOrMissingCountryResolvesToNull() {
        assertNull(CountryFlags.resolveCode(country = "Atlantis", remark = null))
        assertNull(CountryFlags.resolveCode(country = null, remark = null))
        assertNull(CountryFlags.resolveCode(country = "", remark = null))
    }

    @Test
    fun drawableNameIsStableAndLowercased() {
        assertEquals("shroxy_flag_us", CountryFlags.flagDrawableName("US"))
        assertEquals("shroxy_flag_um", CountryFlags.flagDrawableName("um"))
    }

    @Test
    fun drawableLookupHitReturnsId() {
        val id = CountryFlags.flagDrawableRes({ name ->
            if (name == "shroxy_flag_us") 1234 else 0
        }, "us")
        assertEquals(1234, id)
    }

    @Test
    fun drawableLookupMissReturnsNullForBadgeFallback() {
        // "um" has no bundled vector: the miss must resolve to null so the UI shows the badge.
        val id = CountryFlags.flagDrawableRes({ 0 }, "um")
        assertNull(id)
    }

    @Test
    fun drawableLookupIsCached() {
        // Distinct codes from the other lookup tests: the cache is shared object state.
        var calls = 0
        val lookup: (String) -> Int = { name ->
            calls++
            if (name == "shroxy_flag_de") 42 else 0
        }
        assertEquals(42, CountryFlags.flagDrawableRes(lookup, "de"))
        assertEquals(42, CountryFlags.flagDrawableRes(lookup, "de"))
        assertEquals(1, calls)
        // A cached miss stays a miss without re-querying.
        assertNull(CountryFlags.flagDrawableRes(lookup, "fr"))
        assertNull(CountryFlags.flagDrawableRes(lookup, "fr"))
        assertEquals(2, calls)
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
