package com.v2ray.ang.ui.shroxy

import com.v2ray.ang.ui.shroxy.components.ShroxyTrafficFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale

class ShroxyTrafficFormatTest {
    private val enUnits = listOf("B", "KB", "MB", "GB", "TB")
    private val ruUnits = listOf("Б", "КБ", "МБ", "ГБ", "ТВ")
    private val unlimited = "∞"

    @Test
    fun unlimitedTrafficRendersInfinitySymbol() {
        assertEquals(
            "3.8TB/∞",
            ShroxyTrafficFormat.formatTraffic(3_800_000_000_000L, 0L, enUnits, unlimited, Locale.US),
        )
        assertEquals(
            "3,8ТВ/∞",
            ShroxyTrafficFormat.formatTraffic(3_800_000_000_000L, null, ruUnits, unlimited, Locale("ru", "RU")),
        )
    }

    @Test
    fun limitedTrafficRendersUsedOverTotal() {
        assertEquals(
            "3.8TB/10.0TB",
            ShroxyTrafficFormat.formatTraffic(3_800_000_000_000L, 10_000_000_000_000L, enUnits, unlimited, Locale.US),
        )
    }

    @Test
    fun smallTrafficStaysInBaseUnit() {
        assertEquals("512B/∞", ShroxyTrafficFormat.formatTraffic(512L, 0L, enUnits, unlimited, Locale.US))
    }

    @Test
    fun unknownUsageRendersNull() {
        assertNull(ShroxyTrafficFormat.formatTraffic(null, 1000L, enUnits, unlimited, Locale.US))
    }

    @Test
    fun negativeTotalIsTreatedAsUnlimited() {
        assertEquals("1.0GB/∞", ShroxyTrafficFormat.formatTraffic(1_000_000_000L, -5L, enUnits, unlimited, Locale.US))
    }
}
