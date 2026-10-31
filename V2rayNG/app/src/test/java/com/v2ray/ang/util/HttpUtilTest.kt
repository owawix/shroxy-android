package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpUtilTest {

    @Test
    fun isLikelyHtmlDetectsHtmlDocumentsAfterLeadingWhitespace() {
        assertTrue(HttpUtil.isLikelyHtml("<html><body>login</body></html>"))
        assertTrue(HttpUtil.isLikelyHtml("<!DOCTYPE html>"))
        assertTrue(HttpUtil.isLikelyHtml("  \n\t <div>error page</div>"))
    }

    @Test
    fun isLikelyHtmlRejectsSubscriptionPayloads() {
        assertFalse(HttpUtil.isLikelyHtml("vmess://eyJhZGQiOiJleGFtcGxlLmNvbSJ9"))
        assertFalse(HttpUtil.isLikelyHtml("dm1lc3M6Ly9leGFtcGxlLmNvbQ=="))
        assertFalse(HttpUtil.isLikelyHtml(""))
    }

    @Test
    fun shouldRetryWithFallbackUaForHtmlFetchedWithDefaultUserAgent() {
        assertTrue(HttpUtil.shouldRetryWithFallbackUa("<html><body>403</body></html>", null))
        assertTrue(HttpUtil.shouldRetryWithFallbackUa("  \n<div>interstitial</div>", ""))
    }

    @Test
    fun shouldRetryWithFallbackUaSkipsNonHtmlAndEmptyBodies() {
        assertFalse(HttpUtil.shouldRetryWithFallbackUa("vmess://eyJhZGQiOiJleGFtcGxlLmNvbSJ9", null))
        assertFalse(HttpUtil.shouldRetryWithFallbackUa("dm1lc3M6Ly9leGFtcGxlLmNvbQ==", null))
        assertFalse(HttpUtil.shouldRetryWithFallbackUa("", null))
    }

    @Test
    fun shouldRetryWithFallbackUaSkipsWhenFallbackUserAgentAlreadyUsed() {
        assertFalse(HttpUtil.shouldRetryWithFallbackUa("<html>403</html>", "v2rayNG/${BuildConfig.VERSION_NAME}"))
        assertFalse(HttpUtil.shouldRetryWithFallbackUa("<html>403</html>", "v2rayng/custom-client"))
        assertTrue(HttpUtil.shouldRetryWithFallbackUa("<html>403</html>", "ClashForAndroid/2.5"))
    }

    @Test
    fun subscriptionFallbackUserAgentUsesConfiguredPrefixAndAppVersion() {
        assertEquals(
            "${AppConfig.SUBSCRIPTION_FALLBACK_UA_PREFIX}/${BuildConfig.VERSION_NAME}",
            HttpUtil.subscriptionFallbackUserAgent()
        )
        assertTrue(HttpUtil.subscriptionFallbackUserAgent().startsWith("v2rayNG/"))
    }

    @Test
    fun effectiveSubscriptionUserAgentPrefersUserAgentHeaderOverride() {
        // A "User-Agent" custom header overrides the subscription's User-Agent field,
        // matching the transport layer which applies custom headers last.
        assertEquals(
            "clash-verge/1.0",
            HttpUtil.effectiveSubscriptionUserAgent("SubUA/2.0", """{"User-Agent":"clash-verge/1.0"}""")
        )
        // Header names match case-insensitively; the last matching entry wins (OkHttp
        // replaces the earlier case-variant header).
        assertEquals(
            "clash-verge/1.0",
            HttpUtil.effectiveSubscriptionUserAgent("SubUA/2.0", """{"USER-AGENT":"old/9","user-agent":"clash-verge/1.0"}""")
        )
        // Non-UA headers leave the subscription field in effect; no override and a blank
        // field means the built-in default was sent.
        assertEquals("SubUA/2.0", HttpUtil.effectiveSubscriptionUserAgent("SubUA/2.0", """{"X-hwid":"d"}"""))
        assertNull(HttpUtil.effectiveSubscriptionUserAgent(null, """{"X-hwid":"d"}"""))
        assertNull(HttpUtil.effectiveSubscriptionUserAgent(null, null))
    }

    @Test
    fun shouldRetryWithFallbackUaDecidesOnEffectiveUserAgentWithHeaderOverride() {
        // A fallback-prefixed "User-Agent" header override means the fallback was already
        // used: no retry, so no duplicate identical request fires.
        assertFalse(
            HttpUtil.shouldRetryWithFallbackUa(
                "<html>403</html>",
                HttpUtil.effectiveSubscriptionUserAgent("SubUA/2.0", """{"User-Agent":"v2rayNG/${BuildConfig.VERSION_NAME}"}""")
            )
        )
        // A non-fallback override (even over a fallback-prefixed subscription field) sent
        // a blocked UA: the retry must fire.
        assertTrue(
            HttpUtil.shouldRetryWithFallbackUa(
                "<html>403</html>",
                HttpUtil.effectiveSubscriptionUserAgent("v2rayNG/keep", """{"user-agent":"ClashForAndroid/2.5"}""")
            )
        )
    }

    @Test
    fun requestHeadersWithoutUserAgentRemovesOnlyUserAgentEntries() {
        assertEquals(
            linkedMapOf("X-hwid" to "d", "Authorization" to "Bearer t"),
            JsonUtil.parseHeadersToMap(
                HttpUtil.requestHeadersWithoutUserAgent("""{"User-Agent":"clash/1","X-hwid":"d","Authorization":"Bearer t"}""")
            )
        )
        // Headers that collapse to nothing degrade to "no custom headers" (null).
        assertNull(HttpUtil.requestHeadersWithoutUserAgent("""{"user-agent":"clash/1"}"""))
        assertNull(HttpUtil.requestHeadersWithoutUserAgent("{}"))
        assertNull(HttpUtil.requestHeadersWithoutUserAgent(null))
    }
}
