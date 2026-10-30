package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeoUpdateWorkerTest {

    @Test
    fun checkIsDueWhenNeverChecked() {
        assertTrue(GeoUpdateWorker.isCheckDue(0L, 1_000_000L))
        assertTrue(GeoUpdateWorker.isCheckDue(-1L, 1_000_000L))
    }

    @Test
    fun checkIsDueWhenLastCheckOlderThanInterval() {
        val now = 10_000_000L
        assertTrue(GeoUpdateWorker.isCheckDue(now - GeoUpdateWorker.CHECK_INTERVAL_MILLIS, now))
        assertTrue(GeoUpdateWorker.isCheckDue(now - GeoUpdateWorker.CHECK_INTERVAL_MILLIS - 1, now))
    }

    @Test
    fun checkIsNotDueWhenLastCheckWithinInterval() {
        val now = 100_000_000L
        assertFalse(GeoUpdateWorker.isCheckDue(now, now))
        assertFalse(GeoUpdateWorker.isCheckDue(now - GeoUpdateWorker.CHECK_INTERVAL_MILLIS + 1, now))
    }

    @Test
    fun shouldRescheduleWhenStoredVersionBelowCurrent() {
        assertTrue(GeoUpdateWorker.shouldReschedule(0))
        assertTrue(GeoUpdateWorker.shouldReschedule(1))
    }

    @Test
    fun shouldNotRescheduleWhenStoredVersionCurrent() {
        assertFalse(GeoUpdateWorker.shouldReschedule(2))
    }
}
