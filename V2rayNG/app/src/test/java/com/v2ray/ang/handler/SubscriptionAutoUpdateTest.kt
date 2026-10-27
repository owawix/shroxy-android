package com.v2ray.ang.handler

import android.util.Log
import android.webkit.URLUtil
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap

/**
 * Hourly auto-update policy: a newly imported subscription defaults to
 * autoUpdate=true / 60-minute interval and is scheduled immediately (no app restart),
 * and the one-time migration moves pre-existing URL subscriptions onto the same
 * policy exactly once.
 */
class SubscriptionAutoUpdateTest {

    private val mainValues = ConcurrentHashMap<String, String>()
    private val subValues = ConcurrentHashMap<String, String>()
    private val settingsValues = ConcurrentHashMap<String, Boolean>()

    private val scheduledIds = mutableListOf<String>()
    private var rescheduleCount = 0
    private lateinit var defaultScheduler: (String) -> Unit

    @BeforeEach
    fun prepareStorage() {
        defaultScheduler = AngConfigManager.scheduleSubscriptionAutoUpdate
        AngConfigManager.scheduleSubscriptionAutoUpdate = { scheduledIds.add(it) }
        rescheduleCount = 0

        for ((storage, values) in listOf(main to mainValues, subs to subValues)) {
            reset(storage)
            values.clear()
            whenever(storage.decodeString(any())).thenAnswer { values[it.getArgument<String>(0)] }
            whenever(storage.encode(any<String>(), any<String>())).thenAnswer {
                values[it.getArgument(0)] = it.getArgument(1)
                true
            }
            whenever(storage.allKeys()).thenAnswer { values.keys.toTypedArray() }
        }

        reset(settings)
        settingsValues.clear()
        whenever(settings.decodeBool(any(), any())).thenAnswer {
            settingsValues[it.getArgument<String>(0)] ?: it.getArgument(1)
        }
        whenever(settings.encode(any<String>(), any<Boolean>())).thenAnswer {
            settingsValues[it.getArgument(0)] = it.getArgument(1)
            true
        }
    }

    @AfterEach
    fun restoreScheduler() {
        AngConfigManager.scheduleSubscriptionAutoUpdate = defaultScheduler
    }

    @Test
    fun newSubscriptionDefaultsToHourlyAutoUpdateAndIsScheduled() {
        val url = "https://panel.example.com/sub/new"

        withAndroidStubs {
            val (count, countSub) = AngConfigManager.importBatchConfig(url, "", true)

            assertEquals(0, count)
            assertEquals(1, countSub)
        }

        val ids = mainValues["SUB_IDS"]?.let { JsonUtil.fromJsonSafe(it, Array<String>::class.java) }?.toList()
        assertEquals(1, ids?.size)
        val created = savedSubscription(ids!!.first())
        assertEquals(url, created.url)
        assertTrue(created.autoUpdate)
        assertEquals(AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL_MINUTES, created.updateInterval)
        // The worker was scheduled for the created subscription without an app restart.
        assertEquals(listOf(ids.first()), scheduledIds)
    }

    @Test
    fun migrationEnablesHourlyAutoUpdateForUrlSubscriptionsOnly() {
        subValues["sub-1"] = JsonUtil.toJson(
            SubscriptionItem(remarks = "panel", url = "https://panel.example.com/sub/a")
        )
        // Manual subscriptions without a URL are not auto-updated.
        subValues["sub-2"] = JsonUtil.toJson(SubscriptionItem(remarks = "manual", url = ""))
        mainValues["SUB_IDS"] = """["sub-1","sub-2"]"""

        val ran = withLogStub {
            SubscriptionAutoUpdateMigration.run(reschedule = { rescheduleCount++ })
        }

        assertTrue(ran)
        val migrated = savedSubscription("sub-1")
        assertTrue(migrated.autoUpdate)
        assertEquals(AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL_MINUTES, migrated.updateInterval)
        assertEquals("panel", migrated.remarks)
        assertFalse(savedSubscription("sub-2").autoUpdate)
        assertEquals(true, settingsValues[AppConfig.PREF_SHROXY_SUB_MIGRATION_V1])
        assertEquals(1, rescheduleCount)
    }

    @Test
    fun migrationIsIdempotentAndReschedulesOnce() {
        subValues["sub-1"] = JsonUtil.toJson(
            SubscriptionItem(remarks = "panel", url = "https://panel.example.com/sub/a")
        )
        mainValues["SUB_IDS"] = """["sub-1"]"""

        val first = withLogStub {
            SubscriptionAutoUpdateMigration.run(reschedule = { rescheduleCount++ })
        }
        val intervalAfterFirstRun = savedSubscription("sub-1").updateInterval

        // User disables auto-update afterwards: a re-run must not flip it back.
        val item = savedSubscription("sub-1")
        item.autoUpdate = false
        subValues["sub-1"] = JsonUtil.toJson(item)

        val second = withLogStub {
            SubscriptionAutoUpdateMigration.run(reschedule = { rescheduleCount++ })
        }

        assertTrue(first)
        assertFalse(second)
        assertEquals(1, rescheduleCount)
        assertEquals(intervalAfterFirstRun, savedSubscription("sub-1").updateInterval)
        assertFalse(savedSubscription("sub-1").autoUpdate)
    }

    // ---------- helpers ----------

    private fun savedSubscription(guid: String): SubscriptionItem {
        val json = subValues[guid] ?: error("subscription payload missing for $guid")
        return JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java) ?: error("bad json")
    }

    private fun <T> withLogStub(block: () -> T): T =
        mockStatic(Log::class.java).use { block() }

    private fun <T> withAndroidStubs(block: () -> T): T {
        mockStatic(Log::class.java).use { logs ->
            mockStatic(URLUtil::class.java).use { urlUtil ->
                urlUtil.`when`<Boolean> { URLUtil.isHttpsUrl(any()) }.thenAnswer {
                    it.getArgument<String>(0).startsWith("https://")
                }
                return block()
            }
        }
    }

    companion object {
        // MmkvManager binds its storages lazily once per JVM, so all MMKV tests
        // must share the same mock instances via MmkvTestStorage.
        private val main: MMKV = MmkvTestStorage.main
        private val subs: MMKV = MmkvTestStorage.subs
        private val settings: MMKV = MmkvTestStorage.settings

        @BeforeAll
        @JvmStatic
        fun initializeHandles() {
            MmkvTestStorage.initialize()
        }
    }
}
