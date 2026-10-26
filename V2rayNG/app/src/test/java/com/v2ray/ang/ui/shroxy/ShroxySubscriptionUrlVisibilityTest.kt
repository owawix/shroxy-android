package com.v2ray.ang.ui.shroxy

import com.v2ray.ang.ui.shroxy.components.isSubscriptionUrlVisible
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShroxySubscriptionUrlVisibilityTest {
    @Test
    fun nullUrlHidesGlobeAction() {
        assertFalse(isSubscriptionUrlVisible(null))
    }

    @Test
    fun blankUrlHidesGlobeAction() {
        assertFalse(isSubscriptionUrlVisible(""))
        assertFalse(isSubscriptionUrlVisible("   "))
    }

    @Test
    fun realUrlShowsGlobeAction() {
        assertTrue(isSubscriptionUrlVisible("https://example.com/sub"))
    }
}
