package com.v2ray.ang.ui.main

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvTestStorage
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Visibility and copy behavior of the Shroxy empty-state HWID: the value is provisioned
 * into [MainUiState] only when no subscription group is visible, never when groups
 * exist, and [MainAction.CopyHwid] writes the clipboard through the data-source seam
 * and emits the confirmation snackbar.
 */
class MainHwidTest {

    companion object {
        @JvmStatic
        @BeforeAll
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun setUpClass() {
            MmkvTestStorage.initialize()
            // Route LogUtil to the "off" level so unit tests never touch android.util.Log,
            // including LogUtil calls made from Dispatchers.IO threads that mockStatic(Log)
            // cannot intercept deterministically.
            val field = LogUtil::class.java.getDeclaredField("cachedMinPriority")
            field.isAccessible = true
            field.set(LogUtil, Int.MAX_VALUE)
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }

        @JvmStatic
        @AfterAll
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun tearDownClass() {
            Dispatchers.resetMain()
        }
    }

    private fun newViewModel(dataSource: MainDataSource) = MainViewModel(mock<Application>(), dataSource)

    private fun newDataSource(): MainDataSource {
        val dataSource = mock<MainDataSource>()
        runBlocking {
            whenever(dataSource.mainServiceEvent).thenReturn(emptyFlow())
            whenever(dataSource.getSelectedSubscriptionId()).thenReturn("")
            whenever(dataSource.getSelectServer()).thenReturn(null)
            whenever(dataSource.getConfirmRemove()).thenReturn(false)
            whenever(dataSource.getDoubleColumnDisplay()).thenReturn(false)
            whenever(dataSource.getSubscriptions()).thenReturn(emptyList())
            whenever(dataSource.getString(any<Int>())).thenAnswer { "str-${it.getArgument<Int>(0)}" }
        }
        return dataSource
    }

    private fun awaitTrue(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(true, condition())
    }

    @Test
    fun hwidIsProvisionedIntoStateOnlyWhileNoGroupIsVisible() {
        val dataSource = newDataSource()
        runBlocking { whenever(dataSource.ensureHwid()).thenReturn("stubhwid0000001") }
        val viewModel = newViewModel(dataSource)

        awaitTrue(5_000) { viewModel.uiState.value.hwid == "stubhwid0000001" }
        runBlocking { verify(dataSource, timeout(5_000)).ensureHwid() }
    }

    @Test
    fun hwidIsNeverProvisionedWhenGroupsExist() {
        val dataSource = newDataSource()
        runBlocking {
            whenever(dataSource.getSubscriptions())
                .thenReturn(listOf(SubscriptionCache("sub-1", SubscriptionItem())))
            whenever(dataSource.getServerGuidList("sub-1")).thenReturn(listOf("srv-1"))
        }
        val viewModel = newViewModel(dataSource)

        // Wait for the group pass to complete, then confirm the empty-state provisioning
        // never ran: the row must stay hidden while subscriptions exist.
        awaitTrue(5_000) { viewModel.uiState.value.groups.isNotEmpty() }
        assertNull(viewModel.uiState.value.hwid)
        runBlocking { verify(dataSource, never()).ensureHwid() }
    }

    @Test
    fun hostResumeReloadPicksUpGroupImportedExternally() {
        val dataSource = newDataSource()
        runBlocking { whenever(dataSource.ensureHwid()).thenReturn("stubhwid0000001") }
        val viewModel = newViewModel(dataSource)

        // Cold start with no subscription: the empty pass ran and provisioned the HWID.
        awaitTrue(5_000) { viewModel.uiState.value.hwid == "stubhwid0000001" }

        // Simulate an external import (UrlSchemeActivity → AngConfigManager) that wrote
        // MMKV behind this ViewModel while the activity was away.
        runBlocking {
            whenever(dataSource.getSubscriptions())
                .thenReturn(listOf(SubscriptionCache("sub-1", SubscriptionItem())))
            whenever(dataSource.getServerGuidList("sub-1")).thenReturn(listOf("srv-1"))
        }

        viewModel.onHostResumed()

        // The resume-triggered reload re-read storage: the group appears, so the empty
        // state (and its HWID row) leaves the screen without a process restart.
        awaitTrue(5_000) { viewModel.uiState.value.groups.map { it.id } == listOf("sub-1") }
    }

    @Test
    fun hwidProvisioningIsNotRepeatedAfterGroupsReload() {
        val dataSource = newDataSource()
        val ensureHwidCalls = AtomicInteger()
        runBlocking {
            whenever(dataSource.ensureHwid()).thenAnswer {
                ensureHwidCalls.incrementAndGet()
                "stubhwid0000001"
            }
        }
        val viewModel = newViewModel(dataSource)
        awaitTrue(5_000) { viewModel.uiState.value.hwid == "stubhwid0000001" }

        viewModel.onAction(MainAction.RefreshGroups)
        // The reload pass has run once it resolved the subscriptions a second time.
        runBlocking { verify(dataSource, timeout(5_000).times(2)).getSubscriptions() }
        // Give any (incorrect) second provisioning on Dispatchers.IO time to surface.
        Thread.sleep(200)

        assertEquals(1, ensureHwidCalls.get())
        assertEquals("stubhwid0000001", viewModel.uiState.value.hwid)
    }

    @Test
    fun copyHwidWritesClipboardAndEmitsCopiedSnackbar() {
        val dataSource = newDataSource()
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.CopyHwid("stubhwid0000001"))

        runBlocking { verify(dataSource, timeout(5_000)).copyToClipboard("stubhwid0000001") }
        val event = runBlocking {
            withTimeoutOrNull(5_000) { viewModel.snackbarEvent.first() }
        }
        assertEquals("str-${R.string.shroxy_hwid_copied}", event)
    }
}
