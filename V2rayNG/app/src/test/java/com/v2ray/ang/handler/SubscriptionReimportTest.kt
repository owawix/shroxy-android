package com.v2ray.ang.handler

import android.util.Log
import android.webkit.URLUtil
import com.tencent.mmkv.MMKV
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

/**
 * Regression test for the subscription re-import dead-end: a subscription whose first
 * update failed has no servers, so the group filter hides it from the UI and the user
 * can only retry by importing the same URL again. The re-import must request an update
 * (countSub = 1) instead of being deduped into a no-op, and must not duplicate the
 * subscription.
 *
 * The subscription update triggered by countSub > 0 never reaches the network here:
 * android.text.TextUtils is not mocked, so updateConfigViaSub exits through its
 * catch-all before any HTTP call.
 */
class SubscriptionReimportTest {
    // ConcurrentHashMap: the concurrency test imports from two threads at once.
    private val mainValues = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val subValues = java.util.concurrent.ConcurrentHashMap<String, String>()

    @BeforeEach
    fun prepareStorage() {
        // The JVM has no RemoteWorkManager/Application: the scheduling seam is a no-op here.
        AngConfigManager.scheduleSubscriptionAutoUpdate = { }
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
    }

    @Test
    fun reimportingKnownUrlRequestsUpdateWithoutDuplicating() {
        val url = "https://panel.example.com/sub/abc123"
        subValues["sub-1"] = JsonUtil.toJson(SubscriptionItem(remarks = "panel.example.com", url = url))
        mainValues["SUB_IDS"] = """["sub-1"]"""

        withAndroidStubs {
            val (count, countSub) = AngConfigManager.importBatchConfig(url, "", true)

            assertEquals(0, count)
            assertEquals(1, countSub)
        }

        assertEquals("""["sub-1"]""", mainValues["SUB_IDS"])
        assertEquals(listOf("sub-1"), subValues.keys.toList())
    }

    @Test
    fun importingNewUrlCreatesSingleSubscription() {
        val url = "https://panel.example.com/sub/abc123"

        withAndroidStubs {
            val (count, countSub) = AngConfigManager.importBatchConfig(url, "", true)

            assertEquals(0, count)
            assertEquals(1, countSub)
        }

        val ids = mainValues["SUB_IDS"]?.let { JsonUtil.fromJsonSafe(it, Array<String>::class.java) }?.toList()
        assertEquals(1, ids?.size)
        val created = subValues[ids!!.first()] ?: error("created subscription payload missing")
        assertEquals(url, JsonUtil.fromJsonSafe(created, SubscriptionItem::class.java)?.url)
    }

    @Test
    fun concurrentImportsOfSameUrlCreateSingleSubscription() {
        val url = "https://panel.example.com/sub/race"
        val barrier = java.util.concurrent.CyclicBarrier(2)
        val results = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
        val threads = (1..2).map {
            kotlin.concurrent.thread {
                barrier.await()
                withAndroidStubs {
                    results.add(AngConfigManager.importBatchConfig(url, "", true))
                }
            }
        }
        threads.forEach { it.join() }

        // Both imports report the subscription path (retry semantics), but the
        // dedupe+create must be atomic: exactly one record for the URL may exist.
        results.forEach { assertEquals(0 to 1, it) }
        assertEquals(
            1,
            subValues.values.count {
                JsonUtil.fromJsonSafe(it, SubscriptionItem::class.java)?.url == url
            }
        )
    }

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

        @BeforeAll
        @JvmStatic
        fun initializeHandles() {
            MmkvTestStorage.initialize()
        }
    }
}
