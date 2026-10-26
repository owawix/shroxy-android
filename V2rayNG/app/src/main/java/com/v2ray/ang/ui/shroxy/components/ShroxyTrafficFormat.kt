package com.v2ray.ang.ui.shroxy.components

import java.util.Locale

/**
 * Pure traffic formatting for the subscription info strip ("3.8 TB/∞").
 * Unit labels are supplied by resources so every locale stays in sync with the strings file.
 */
object ShroxyTrafficFormat {

    fun formatBytes(bytes: Long, units: List<String>, locale: Locale = Locale.getDefault()): String {
        require(units.isNotEmpty()) { "units must not be empty" }
        var value = bytes.coerceAtLeast(0L).toDouble()
        var unitIndex = 0
        while (value >= 1000.0 && unitIndex < units.lastIndex) {
            value /= 1000.0
            unitIndex++
        }
        val amount = if (unitIndex == 0) {
            value.toLong().toString()
        } else {
            String.format(locale, "%.1f", value)
        }
        return "$amount${units[unitIndex]}"
    }

    /** Returns "used/total"; a null or non-positive total renders as [unlimited]. Null when usage is unknown. */
    fun formatTraffic(
        usedBytes: Long?,
        totalBytes: Long?,
        units: List<String>,
        unlimited: String,
        locale: Locale = Locale.getDefault(),
    ): String? {
        if (usedBytes == null) return null
        val used = formatBytes(usedBytes, units, locale)
        val total = if (totalBytes == null || totalBytes <= 0L) {
            unlimited
        } else {
            formatBytes(totalBytes, units, locale)
        }
        return "$used/$total"
    }
}
