package com.v2ray.ang.handler

import android.text.TextUtils
import android.util.Log
import com.tencent.mmkv.MMKV
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * A failed subscription update must persist a stable, locale-agnostic error code in
 * SubscriptionItem.lastError (never a raw message with host:port), and a successful
 * update must clear it. Drives the real updateConfigViaSub against a loopback
 * HttpServer: HTTP error status, garbage body, empty body, refused connection, and a
 * valid vmess payload.
 */
class SubscriptionLastErrorTest {

    private val mainValues = ConcurrentHashMap<String, String>()
    private val subValues = ConcurrentHashMap<String, String>()

    @BeforeEach
    fun prepareStorage() {
        for ((storage, values) in listOf(main to mainValues, subs to subValues, profileFull to profileValues)) {
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
    fun httpErrorStatusPersistsHttpCode() {
        withServer(httpResponse("403 Forbidden", "forbidden")) { url ->
            val result = runUpdate(url)

            assertEquals(1, result.failureCount)
            val saved = savedSubscription(url)
            assertEquals("http_403", saved.lastError)
            assertNoHostLeak(saved.lastError)
        }
    }

    @Test
    fun garbageBodyPersistsParseCode() {
        withServer(httpResponse("200 OK", "this is not a subscription")) { url ->
            val result = runUpdate(url)

            assertEquals(1, result.failureCount)
            val saved = savedSubscription(url)
            assertEquals("parse", saved.lastError)
            assertNoHostLeak(saved.lastError)
        }
    }

    @Test
    fun emptyBodyPersistsEmptyCode() {
        withServer(httpResponse("200 OK", "")) { url ->
            val result = runUpdate(url)

            assertEquals(1, result.failureCount)
            assertEquals("empty", savedSubscription(url).lastError)
        }
    }

    @Test
    fun refusedConnectionPersistsNetworkCode() {
        val deadPort = ServerSocket(0).use { it.localPort }
        val url = "http://127.0.0.1:$deadPort/sub"

        val result = runUpdate(url)

        assertEquals(1, result.failureCount)
        val saved = savedSubscription(url)
        assertEquals("network", saved.lastError)
        assertNoHostLeak(saved.lastError)
    }

    @Test
    fun failedUpdateDoesNotAdvanceLastUpdated() {
        withServer(httpResponse("200 OK", "this is not a subscription")) { url ->
            val result = runUpdate(url)

            assertEquals(1, result.failureCount)
            assertEquals(-1L, savedSubscription(url).lastUpdated)
        }
    }

    @Test
    fun successfulUpdateClearsLastError() {
        withServer(httpResponse("200 OK", VMESS_LINE)) { url ->
            val result = runUpdate(url, lastError = "http_403", stubProfileWrites = true)

            assertEquals(1, result.successCount)
            val saved = savedSubscription(url)
            assertNull(saved.lastError)
            assertTrue(saved.lastUpdated > 0)
        }
    }

    // ---------- helpers ----------

    private fun runUpdate(url: String, lastError: String? = null, stubProfileWrites: Boolean = false) =
        withAndroidStubs {
            val item = SubscriptionItem(remarks = "test-sub", url = url, lastError = lastError)
            val update = { AngConfigManager.updateConfigViaSub(SubscriptionCache("sub-1", item)) }
            if (stubProfileWrites) withProfileWriteStub(update) else update()
        }

    private fun savedSubscription(url: String): SubscriptionItem {
        val json = subValues["sub-1"] ?: error("subscription not persisted")
        val saved = JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java) ?: error("bad json")
        assertEquals(url, saved.url)
        return saved
    }

    private fun <T> withAndroidStubs(block: () -> T): T {
        mockStatic(Log::class.java).use {
            mockStatic(TextUtils::class.java).use { textUtils ->
                textUtils.`when`<Boolean> { TextUtils.isEmpty(any()) }.thenAnswer { answer ->
                    (answer.getArgument(0) as? CharSequence).isNullOrEmpty()
                }
                return withLoopbackUrlValidation(block)
            }
        }
    }

    /**
     * The loopback URL fails the real URL validators on the JVM (android.util.Patterns is
     * stubbed), so swap Utils.INSTANCE for a delegate that accepts the loopback test URL
     * and stubs the few behaviors the update/parse flow needs.
     */
    private fun <T> withLoopbackUrlValidation(block: () -> T): T {
        val delegate = Mockito.mock(Utils::class.java) { invocation ->
            when (invocation.method.name) {
                "isValidUrl", "isValidSubUrl" -> true
                // Xray mode: getHttpPort() == getSocksPort(), and the mocked parseInt
                // below returns 0, so no local-proxy pre-attempt is made at all.
                "isXray" -> true
                "getUuid" -> java.util.UUID.randomUUID().toString()
                "decode" -> base64Decode(invocation.arguments[0] as String?)
                else -> defaultValue(invocation.method.returnType)
            }
        }
        return swapSingletonInstance(Utils::class.java, delegate, block)
    }

    /**
     * Successful updates persist parsed profiles through MmkvManager.saveServerProfiles,
     * which takes a native MMKV lock that cannot run on the JVM. Swap MmkvManager.INSTANCE
     * for a stub that keeps subscription persistence real (the subject of this test) and
     * no-ops the profile-index write.
     */
    private fun <T> withProfileWriteStub(block: () -> T): T {
        val delegate = Mockito.mock(MmkvManager::class.java) { invocation ->
            when (invocation.method.name) {
                "saveServerProfiles" -> null
                "encodeSubscription" -> {
                    val guid = invocation.arguments[0] as String
                    val item = invocation.arguments[1] as SubscriptionItem
                    val key = guid.ifBlank { java.util.UUID.randomUUID().toString() }
                    subValues[key] = JsonUtil.toJson(item)
                    null
                }
                else -> defaultValue(invocation.method.returnType)
            }
        }
        return swapSingletonInstance(MmkvManager::class.java, delegate, block)
    }

    /**
     * Swaps a Kotlin object INSTANCE field (static final, unwritable via plain reflection
     * on modern JDKs, and hidden from compilation by --release) via sun.misc.Unsafe
     * through runtime reflection. The original instance is restored in a finally block.
     */
    private fun <T> swapSingletonInstance(clazz: Class<*>, replacement: Any, block: () -> T): T {
        val instanceField = clazz.getDeclaredField("INSTANCE")
        val base = unsafeInvoke("staticFieldBase", instanceField)
        val offset = unsafeInvoke("staticFieldOffset", instanceField) as Long
        val real = unsafeInvoke("getObject", base, offset)
        unsafePut(base, offset, replacement)
        try {
            return block()
        } finally {
            unsafePut(base, offset, real)
        }
    }

    private fun unsafeInvoke(name: String, vararg args: Any?): Any? {
        val method = UNSAFE_CLASS.methods.first { it.name == name && it.parameterCount == args.size }
        return method.invoke(UNSAFE, *args)
    }

    private fun unsafePut(base: Any?, offset: Long, value: Any?) {
        UNSAFE_CLASS.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)
            .invoke(UNSAFE, base, offset, value)
    }

    /** Mirrors Utils.decode closely enough for subscription parsing. */
    private fun base64Decode(text: String?): String {
        if (text == null) return ""
        return strictBase64(text) ?: strictBase64(text.trimEnd('=')) ?: ""
    }

    private fun strictBase64(text: String): String? = try {
        String(java.util.Base64.getDecoder().decode(text), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> 0.toChar()
        else -> null
    }

    /**
     * Minimal fixed-response HTTP server on a loopback ServerSocket (com.sun.net.httpserver
     * is not on the unit-test classpath). Answers every connection with the same raw
     * response; updateConfigViaSub may connect twice (proxy attempt, then direct).
     */
    private fun withServer(response: String, block: (String) -> Unit) {
        val listener = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val responder = thread {
            while (!listener.isClosed) {
                try {
                    listener.accept().use { socket ->
                        socket.getOutputStream().write(response.toByteArray())
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
        try {
            block("http://127.0.0.1:${listener.localPort}/sub")
        } finally {
            listener.close()
            responder.join(2000)
        }
    }

    private fun httpResponse(code: String, body: String): String {
        val bytes = body.toByteArray()
        return "HTTP/1.1 $code\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n$body"
    }

    private fun assertNoHostLeak(code: String?) {
        assertEquals(false, code?.contains("127.0.0.1"))
        assertEquals(false, code?.contains("://"))
        assertEquals(false, code?.contains(":"))
    }

    companion object {
        private val main: MMKV = MmkvTestStorage.main
        private val subs: MMKV = MmkvTestStorage.subs
        private val profileFull: MMKV = MmkvTestStorage.profileFull
        private val profileValues = ConcurrentHashMap<String, String>()

        @Suppress("UNCHECKED_CAST")
        private val UNSAFE_CLASS: Class<*> = Class.forName("sun.misc.Unsafe")
        private val UNSAFE: Any = run {
            val field = UNSAFE_CLASS.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null)
        }

        // Minimal but valid vmess line: base64 of a v2rayN vmess JSON.
        private val VMESS_LINE = "vmess://" + java.util.Base64.getEncoder().encodeToString(
            """{"v":"2","ps":"test","add":"127.0.0.1","port":"1080","id":"11111111-2222-3333-4444-555555555555","aid":"0","net":"tcp","type":"none","host":"","path":"","tls":""}"""
                .toByteArray()
        )

        @BeforeAll
        @JvmStatic
        fun initializeHandles() {
            MmkvTestStorage.initialize()
        }
    }
}
