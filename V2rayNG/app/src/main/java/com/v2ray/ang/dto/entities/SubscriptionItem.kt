package com.v2ray.ang.dto.entities

data class SubscriptionItem(
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    val addedTime: Long = System.currentTimeMillis(),
    var lastUpdated: Long = -1,
    var autoUpdate: Boolean = false,
    var updateInterval: Long = 1440, // in minutes, default to 24 hours
    var prevProfile: String? = null,
    var nextProfile: String? = null,
    var filter: String? = null,
    var allowInsecureUrl: Boolean = false,
    var userAgent: String? = null,
    var requestHeaders: String? = null,
    var usageInfo: SubscriptionUsageInfo? = null,
    /**
     * Stable, locale-agnostic code of the last failed update ("network", "http_403",
     * "parse", "empty"), or null when the last update succeeded / never ran. Only codes
     * are persisted here — never raw exception messages, which can contain host:port.
     * UI layers map codes to localized text. Adding this field is backward-compatible
     * with stored MMKV JSON (absent key deserializes to null).
     */
    var lastError: String? = null,
)

/**
 * Stable subscription-update error codes persisted in [SubscriptionItem.lastError].
 * See the field documentation for the security constraint on persisted values.
 */
object SubscriptionErrorCode {
    const val NETWORK = "network"
    const val PARSE = "parse"
    const val EMPTY = "empty"
    const val HTTP_PREFIX = "http_"

    fun http(statusCode: Int) = "$HTTP_PREFIX$statusCode"
}

