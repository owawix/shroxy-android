package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionErrorCode
import com.v2ray.ang.util.HttpUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * A non-2xx subscription response carrying a truthy "x-hwid-max-devices-reached"
 * header (Remnawave device-limit signal) must classify as [SubscriptionErrorCode.DEVICES]
 * on any status; without it the existing http/network classification is unchanged.
 */
class SubscriptionDeviceLimitTest {

    @Test
    fun truthyDeviceLimitHeaderClassifiesAsDevicesOnAnyStatus() {
        val forbidden = HttpUtil.SubscriptionFetchException(
            statusCode = 403,
            responseHeaders = mapOf("X-hwid-Max-Devices-Reached" to "true"),
        )
        assertEquals(SubscriptionErrorCode.DEVICES, AngConfigManager.classifySubscriptionFetchError(forbidden))

        val serverError = HttpUtil.SubscriptionFetchException(
            statusCode = 500,
            responseHeaders = mapOf("x-hwid-max-devices-reached" to "1"),
        )
        assertEquals(SubscriptionErrorCode.DEVICES, AngConfigManager.classifySubscriptionFetchError(serverError))
    }

    @Test
    fun nonTruthyDeviceLimitHeaderKeepsHttpClassification() {
        val error = HttpUtil.SubscriptionFetchException(
            statusCode = 429,
            responseHeaders = mapOf("x-hwid-max-devices-reached" to "false"),
        )
        assertEquals(SubscriptionErrorCode.http(429), AngConfigManager.classifySubscriptionFetchError(error))
    }

    @Test
    fun absentDeviceLimitHeaderKeepsHttpClassification() {
        val error = HttpUtil.SubscriptionFetchException(
            statusCode = 500,
            responseHeaders = mapOf("Content-Type" to "text/html"),
        )
        assertEquals(SubscriptionErrorCode.http(500), AngConfigManager.classifySubscriptionFetchError(error))
    }

    @Test
    fun plainIoExceptionWithoutStatusStillClassifiesAsNetwork() {
        assertEquals(SubscriptionErrorCode.NETWORK, AngConfigManager.classifySubscriptionFetchError(IOException("timeout")))
    }
}
