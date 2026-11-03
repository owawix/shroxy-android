package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.security.SecureRandom
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HwidTest {

    private val main = MmkvTestStorage.main
    private val mainValues = ConcurrentHashMap<String, String>()

    @BeforeEach
    fun prepareStorage() {
        mainValues.clear()
        reset(main)
        whenever(main.decodeString(any())).thenAnswer { mainValues[it.getArgument<String>(0)] }
        whenever(main.encode(any<String>(), any<String>())).thenAnswer {
            val value = it.getArgument<String?>(1)
            if (value == null) {
                mainValues.remove(it.getArgument<String>(0))
            } else {
                mainValues[it.getArgument(0)] = value
            }
            true
        }
    }

    @Test
    fun generatedHwidHasFixedLengthAndAlphabet() {
        repeat(200) {
            val hwid = MmkvManager.generateHwid(SecureRandom())
            assertEquals(15, hwid.length, "unexpected length: $hwid")
            assertTrue(
                hwid.all { it in 'a'..'z' || it in '0'..'9' },
                "unexpected character in $hwid",
            )
        }
    }

    @Test
    fun generateHwidIsDeterministicForSeededRandom() {
        val seed = 20261103L
        assertEquals(
            MmkvManager.generateHwid(Random(seed)),
            MmkvManager.generateHwid(Random(seed)),
        )
    }

    @Test
    fun generateHwidDiffersAcrossSeeds() {
        val seeded = MmkvManager.generateHwid(Random(1L))
        repeat(10) { attempt ->
            assertTrue(seeded != MmkvManager.generateHwid(Random(attempt + 10L)))
        }
    }

    @Test
    fun ensureHwidPersistsOnFirstCallAndIsStableAfterwards() {
        val first = MmkvManager.ensureHwid()
        assertEquals(15, first.length)
        assertTrue(first.all { it in 'a'..'z' || it in '0'..'9' })
        assertEquals(first, mainValues["pref_shroxy_hwid"])
        assertEquals(first, MmkvManager.decodeHwid())

        val second = MmkvManager.ensureHwid()
        assertEquals(first, second)
        assertEquals(first, mainValues["pref_shroxy_hwid"])
    }

    @Test
    fun ensureHwidReturnsStoredValueWithoutRegenerating() {
        val stored = "0123456789abcde"
        mainValues["pref_shroxy_hwid"] = stored

        assertEquals(stored, MmkvManager.ensureHwid())
        assertEquals(stored, MmkvManager.ensureHwid())
        assertEquals(stored, mainValues["pref_shroxy_hwid"])
    }

    @Test
    fun ensureHwidYieldsExactlyOneValueUnderConcurrentFirstUse() {
        val threads = 16
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = (1..threads).map {
                pool.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        MmkvManager.ensureHwid()
                    },
                )
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val hwids = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, hwids.toSet().size, "expected a single HWID, got: $hwids")
            assertEquals(hwids.first(), mainValues["pref_shroxy_hwid"])
        } finally {
            pool.shutdownNow()
        }
    }

    companion object {
        // MmkvManager binds its storages lazily once per JVM, so all MMKV tests
        // must share the same mock instances via MmkvTestStorage.
        @BeforeAll
        @JvmStatic
        fun initializeHandles() {
            MmkvTestStorage.initialize()
        }
    }
}
