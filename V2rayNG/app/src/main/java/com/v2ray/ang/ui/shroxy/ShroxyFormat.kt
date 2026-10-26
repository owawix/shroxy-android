package com.v2ray.ang.ui.shroxy

import com.v2ray.ang.ui.shroxy.components.ShroxyPingUiState

/**
 * Pure formatting helpers for the Shroxy main screen (unit-testable, no Android deps).
 */

/**
 * Formats a connected-session duration as hh:mm:ss with zero-padded fields.
 * Hours are the total hours (not modulo 24) so long sessions keep counting up.
 */
fun formatSessionDuration(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val hours = clamped / 3600
    val minutes = clamped % 3600 / 60
    val seconds = clamped % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/** Normalizes a ping measurement for display: whole milliseconds, never negative. */
fun formatPingMillis(millis: Long): Long = millis.coerceAtLeast(0)

/**
 * Display form of a per-server bulk-test delay. The composable maps [Value] to the
 * "%1$d ms" resource and [NotAvailable] to the "N/A" resource; null means no slot.
 */
sealed interface DelayText {
    data class Value(val millis: Long) : DelayText
    data object NotAvailable : DelayText
}

/**
 * Maps a stored bulk-test delay to its display form: a negative value is the codebase's
 * failed/unreachable sentinel ([DelayText.NotAvailable]); 0 or null means not measured
 * and hides the trailing slot (existing behavior).
 */
fun formatServerDelay(millis: Long?): DelayText? = when {
    millis == null || millis == 0L -> null
    millis < 0L -> DelayText.NotAvailable
    else -> DelayText.Value(millis)
}

/**
 * Maps the main-screen connection state to the test/ping row state: a running test wins;
 * a finished non-negative measurement stays visible as the result until the next run.
 */
fun pingUiState(isTesting: Boolean, resultMillis: Long?): ShroxyPingUiState = when {
    isTesting -> ShroxyPingUiState.Running
    resultMillis != null && resultMillis >= 0 -> ShroxyPingUiState.Result(resultMillis)
    else -> ShroxyPingUiState.Idle
}

/** The empty state is visible only when there is no subscription group at all. */
fun showEmptyState(groupCount: Int): Boolean = groupCount == 0
