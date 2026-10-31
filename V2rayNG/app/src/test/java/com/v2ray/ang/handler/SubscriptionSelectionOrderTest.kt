package com.v2ray.ang.handler

import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.tencent.mmkv.MMKV
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.util.Base64 as JavaBase64

/**
 * Regression test for subscription import selection: a fresh import or an update without
 * a usable previous selection must select the TOP row of the resulting list (the first
 * payload line), not the last one. The published group index keeps the payload order
 * [A, B, C], so the display order is unchanged.
 *
 * The payload goes through the real import pipeline (AngConfigManager.importBatchConfig
 * -> parseBatchConfig -> MmkvManager.saveServerProfiles). The JVM has no Android runtime,
 * so Log, TextUtils and Base64 are statically stubbed.
 */
class SubscriptionSelectionOrderTest {
    private val mainValues = mutableMapOf<String, String>()
    private val subValues = mutableMapOf<String, String>()
    private val profileValues = mutableMapOf<String, String>()

    @BeforeEach
    fun prepareStorage() {
        for ((storage, values) in listOf(
            main to mainValues,
            subs to subValues,
            profileFull to profileValues,
        )) {
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
    fun freshImportSelectsTopRowAndKeepsPayloadOrder() {
        val count = import(payload("A", "B", "C"), append = false)

        assertEquals(3, count)
        assertEquals(listOf("A", "B", "C"), serverRemarks())
        val selected = MmkvManager.getSelectServer()
        assertEquals(serverList().first(), selected)
        assertEquals("A", selectedRemarks())
    }

    @Test
    fun appendImportSelectsTopOfAddedBatchWhenNoSelection() {
        import(payload("A", "B"), append = false)
        // Simulate a store without a previous selection before the append import.
        mainValues.remove("SELECTED_SERVER")

        val count = import(payload("X", "Y", "Z"), append = true)

        assertEquals(3, count)
        // The added batch sits above the existing rows; display order stays payload order.
        assertEquals(listOf("X", "Y", "Z", "A", "B"), serverRemarks())
        val selected = MmkvManager.getSelectServer()
        assertEquals(serverList().first(), selected)
        assertEquals("X", selectedRemarks())
    }

    @Test
    fun updateWithMatchingPayloadKeepsRematchedSelection() {
        import(payload("A", "B", "C"), append = false)
        val oldGuid = guidOf("B")
        MmkvManager.setSelectServer(oldGuid)

        import(payload("A", "B", "C"), append = false)

        // GUIDs regenerate on every update; the selection must follow the rematched row.
        val list = serverList()
        assertEquals(listOf("A", "B", "C"), serverRemarks())
        assertFalse(list.contains(oldGuid))
        assertEquals(guidOf("B"), MmkvManager.getSelectServer())
        assertEquals("B", selectedRemarks())
    }

    @Test
    fun updateWithoutMatchingPayloadSelectsTopRow() {
        import(payload("A", "B", "C"), append = false)
        MmkvManager.setSelectServer(guidOf("B"))

        val count = import(payload("X", "Y", "Z"), append = false)

        assertEquals(3, count)
        assertEquals(listOf("X", "Y", "Z"), serverRemarks())
        val selected = MmkvManager.getSelectServer()
        assertEquals(serverList().first(), selected)
        assertEquals("X", selectedRemarks())
    }

    private fun payload(vararg remarks: String): String =
        remarks.map { vmessLine(it) }.joinToString("\n")

    private fun vmessLine(remarks: String): String {
        val id = java.util.UUID.randomUUID().toString()
        val json =
            """{"v":"2","ps":"$remarks","add":"$remarks.example.com","port":"443","id":"$id","aid":"0","net":"tcp","type":"none","host":"","path":"","tls":""}"""
        return "vmess://" + JavaBase64.getEncoder().encodeToString(json.toByteArray())
    }

    private fun import(payload: String, append: Boolean): Int =
        withAndroidStubs {
            AngConfigManager.importBatchConfig(payload, SUB_ID, append).first
        }

    private fun serverList(): List<String> = MmkvManager.decodeServerList(SUB_ID)

    private fun serverRemarks(): List<String?> =
        serverList().map { guid -> MmkvManager.decodeServerConfig(guid)?.remarks }

    private fun guidOf(remarks: String): String =
        serverList().first { guid -> MmkvManager.decodeServerConfig(guid)?.remarks == remarks }

    private fun selectedRemarks(): String? =
        MmkvManager.getSelectServer()?.let { guid -> MmkvManager.decodeServerConfig(guid)?.remarks }

    private fun <T> withAndroidStubs(block: () -> T): T {
        mockStatic(Log::class.java).use {
            mockStatic(TextUtils::class.java).use { textUtils ->
                textUtils.`when`<Boolean> { TextUtils.isEmpty(any()) }.thenAnswer { call ->
                    call.getArgument<CharSequence?>(0).isNullOrEmpty()
                }
                mockStatic(Base64::class.java).use { base64 ->
                    // Decode with flag handling, like ShadowsocksFmtTest.
                    base64.`when`<ByteArray> {
                        Base64.decode(Mockito.anyString(), Mockito.anyInt())
                    }.thenAnswer { invocation ->
                        val input = invocation.arguments[0] as String
                        val flags = invocation.arguments[1] as Int
                        val decoder = if (flags and Base64.URL_SAFE != 0) {
                            JavaBase64.getUrlDecoder()
                        } else {
                            JavaBase64.getDecoder()
                        }
                        decoder.decode(input)
                    }
                    return block()
                }
            }
        }
    }

    companion object {
        private const val SUB_ID = "sub-selection-order"

        // MmkvManager binds its storages lazily once per JVM, so all MMKV tests
        // must share the same mock instances via MmkvTestStorage.
        private val main: MMKV = MmkvTestStorage.main
        private val subs: MMKV = MmkvTestStorage.subs
        private val profileFull: MMKV = MmkvTestStorage.profileFull

        @BeforeAll
        @JvmStatic
        fun initializeHandles() {
            MmkvTestStorage.initialize()
        }
    }
}
