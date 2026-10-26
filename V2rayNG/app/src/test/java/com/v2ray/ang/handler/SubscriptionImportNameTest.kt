package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class SubscriptionImportNameTest {

    @Test
    fun urlFragmentBecomesTheSubscriptionName() {
        val uri = URI("https://sub.example.com/api/v1/client/subscribe?token=abc#My%20VPN")

        assertEquals("My VPN", AngConfigManager.subscriptionImportName(uri))
    }

    @Test
    fun hostIsTheFallbackWhenTheUrlHasNoFragment() {
        val uri = URI("https://panel.example.com:8443/sub/abc123")

        assertEquals("panel.example.com", AngConfigManager.subscriptionImportName(uri))
    }

    @Test
    fun blankFragmentFallsBackToHost() {
        val uri = URI("https://panel.example.com/sub#")

        assertEquals("panel.example.com", AngConfigManager.subscriptionImportName(uri))
    }

    @Test
    fun genericFallbackIsUsedWhenHostAndFragmentAreMissing() {
        assertEquals("import sub", AngConfigManager.subscriptionImportName(URI("file:///subscribe")))
    }

    @Test
    fun legacyImportSubRemarksIsStillReplaceable() {
        // Subscriptions imported before named imports existed keep the literal "import sub";
        // it never matches the recomputed host name, so Profile-Title must still be adopted.
        val subscription = SubscriptionItem(
            remarks = "import sub",
            url = "https://panel.example.com/sub/abc123",
        )

        assertTrue(AngConfigManager.hasAutoImportName(subscription))
    }

    @Test
    fun legacyImportSubRemarksIsReplaceableEvenWithoutHost() {
        val subscription = SubscriptionItem(
            remarks = "import sub",
            url = "",
        )

        assertTrue(AngConfigManager.hasAutoImportName(subscription))
    }

    @Test
    fun userChosenNameIsNotReplaceable() {
        val subscription = SubscriptionItem(
            remarks = "My VPN",
            url = "https://panel.example.com/sub/abc123",
        )

        assertFalse(AngConfigManager.hasAutoImportName(subscription))
    }
}
