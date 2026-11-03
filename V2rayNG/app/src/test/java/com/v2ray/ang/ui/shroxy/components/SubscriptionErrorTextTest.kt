package com.v2ray.ang.ui.shroxy.components

import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionErrorTextTest {

    @Test
    fun nullOrBlankCodeRendersNothing() {
        assertNull(subscriptionErrorText(null))
        assertNull(subscriptionErrorText(""))
    }

    @Test
    fun networkCodeMapsToNetworkString() {
        assertEquals(R.string.shroxy_sub_error_network, subscriptionErrorText("network")?.resId)
    }

    @Test
    fun parseCodeMapsToParseString() {
        assertEquals(R.string.shroxy_sub_error_parse, subscriptionErrorText("parse")?.resId)
    }

    @Test
    fun emptyCodeMapsToEmptyString() {
        assertEquals(R.string.shroxy_sub_error_empty, subscriptionErrorText("empty")?.resId)
    }

    @Test
    fun devicesCodeMapsToDevicesString() {
        assertEquals(R.string.shroxy_sub_error_devices, subscriptionErrorText(SubscriptionErrorCode.DEVICES)?.resId)
    }

    @Test
    fun httpCodeExposesDigitsOnlyAsFormatArgument() {
        val mapped = subscriptionErrorText("http_403")

        assertEquals(R.string.shroxy_sub_error_http, mapped?.resId)
        assertEquals("403", mapped?.httpStatus)
        assertEquals(false, mapped?.httpStatus?.contains("://"))
    }

    @Test
    fun malformedHttpCodeFallsBackToGeneric() {
        assertEquals(R.string.shroxy_sub_error_generic, subscriptionErrorText("http_")?.resId)
        assertEquals(R.string.shroxy_sub_error_generic, subscriptionErrorText("http_forbidden")?.resId)
        assertEquals(R.string.shroxy_sub_error_generic, subscriptionErrorText("whatever")?.resId)
    }
}
