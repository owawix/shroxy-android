package com.v2ray.ang.ui.shroxy

import com.v2ray.ang.ui.shroxy.components.ShroxyPingUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShroxyFormatTest {

    @Test
    fun sessionDurationFormatsZero() {
        assertEquals("00:00:00", formatSessionDuration(0))
    }

    @Test
    fun sessionDurationFormatsHoursMinutesSeconds() {
        assertEquals("01:15:09", formatSessionDuration(4509))
    }

    @Test
    fun sessionDurationCountsBeyondTwentyFourHours() {
        assertEquals("25:01:01", formatSessionDuration(90_061))
    }

    @Test
    fun sessionDurationClampsNegativeValues() {
        assertEquals("00:00:00", formatSessionDuration(-5))
    }

    @Test
    fun pingMillisPassesPositiveValuesThrough() {
        assertEquals(122, formatPingMillis(122))
    }

    @Test
    fun pingMillisClampsNegativeValues() {
        assertEquals(0, formatPingMillis(-1))
    }

    @Test
    fun pingUiStateIsRunningWhileTesting() {
        assertEquals(ShroxyPingUiState.Running, pingUiState(isTesting = true, resultMillis = 122))
        assertEquals(ShroxyPingUiState.Running, pingUiState(isTesting = true, resultMillis = null))
    }

    @Test
    fun pingUiStateKeepsFinishedResultWhileIdle() {
        assertEquals(ShroxyPingUiState.Result(122), pingUiState(isTesting = false, resultMillis = 122))
    }

    @Test
    fun pingUiStateDropsFailedOrMissingResult() {
        assertEquals(ShroxyPingUiState.Idle, pingUiState(isTesting = false, resultMillis = -1))
        assertEquals(ShroxyPingUiState.Idle, pingUiState(isTesting = false, resultMillis = null))
    }

    @Test
    fun emptyStateVisibleOnlyWithoutGroups() {
        assertTrue(showEmptyState(0))
        assertFalse(showEmptyState(1))
        assertFalse(showEmptyState(3))
    }

    @Test
    fun serverDelayHidesNullAndUnmeasured() {
        assertEquals(null, formatServerDelay(null))
        assertEquals(null, formatServerDelay(0L))
    }

    @Test
    fun serverDelayMarksFailedTestAsNotAvailable() {
        assertEquals(DelayText.NotAvailable, formatServerDelay(-1L))
        assertEquals(DelayText.NotAvailable, formatServerDelay(-100L))
    }

    @Test
    fun serverDelayKeepsPositiveValue() {
        assertEquals(DelayText.Value(145), formatServerDelay(145L))
    }
}
