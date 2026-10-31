package com.v2ray.ang.handler

import com.tencent.mmkv.MMKV
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock

/**
 * Shared MMKV mocks for unit tests.
 *
 * MmkvManager binds its storages lazily on the first access and keeps them for the whole
 * JVM run, so every test class that touches MmkvManager must share the same mock instances.
 */
object MmkvTestStorage {

    val main: MMKV = mock()
    val subs: MMKV = mock()
    val settings: MMKV = mock()
    val profileFull: MMKV = mock()
    val serverRaw: MMKV = mock()
    val serverAff: MMKV = mock()

    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        initialized = true
        mockStatic(MMKV::class.java).use {
            it.`when`<MMKV> { MMKV.mmkvWithID("MAIN", MMKV.MULTI_PROCESS_MODE) }.thenReturn(main)
            it.`when`<MMKV> { MMKV.mmkvWithID("SUB", MMKV.MULTI_PROCESS_MODE) }.thenReturn(subs)
            it.`when`<MMKV> { MMKV.mmkvWithID("SETTING", MMKV.MULTI_PROCESS_MODE) }.thenReturn(settings)
            it.`when`<MMKV> { MMKV.mmkvWithID("PROFILE_FULL_CONFIG", MMKV.MULTI_PROCESS_MODE) }.thenReturn(profileFull)
            it.`when`<MMKV> { MMKV.mmkvWithID("SERVER_RAW", MMKV.MULTI_PROCESS_MODE) }.thenReturn(serverRaw)
            it.`when`<MMKV> { MMKV.mmkvWithID("SERVER_AFF", MMKV.MULTI_PROCESS_MODE) }.thenReturn(serverAff)
            // Force storage initialization while the static mock is still active.
            MmkvManager.getSelectServer()
            MmkvManager.decodeServerConfig("test-initialize")
            MmkvManager.decodeServerRaw("test-initialize")
            MmkvManager.decodeServerAffiliationInfo("test-initialize")
            MmkvManager.decodeSubscriptions()
            MmkvManager.decodeSettingsString("test-initialize")
            // The JVM has no MMKV native library: the cross-process lock and
            // removeValuesForKeys are unmockable native methods and would throw
            // UnsatisfiedLinkError. Substitute no-ops for the whole JVM run (see the
            // seams in MmkvManager).
            MmkvManager.acquireProfileIndexLock = { }
            MmkvManager.releaseProfileIndexLock = { }
            MmkvManager.removeProfilePayloadsHandler = { }
        }
    }
}
