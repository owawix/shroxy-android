package com.v2ray.ang.ui.main

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvTestStorage
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MainViewModelTest {

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

    @Test
    fun toggleGroupExpandedAddsAndRemovesGroupId() {
        val viewModel = newViewModel(newDataSource())

        assertEquals(emptySet<String>(), viewModel.uiState.value.expandedGroupIds)
        viewModel.onAction(MainAction.ToggleGroupExpanded("group-1", expanded = true))
        assertEquals(setOf("group-1"), viewModel.uiState.value.expandedGroupIds)
        viewModel.onAction(MainAction.ToggleGroupExpanded("group-1", expanded = false))
        assertEquals(emptySet<String>(), viewModel.uiState.value.expandedGroupIds)
    }

    @Test
    fun collapseAllClearsEveryExpandedGroupId() {
        val viewModel = newViewModel(newDataSource())
        viewModel.onAction(MainAction.ToggleGroupExpanded("group-1", expanded = true))
        viewModel.onAction(MainAction.ToggleGroupExpanded("group-2", expanded = true))
        assertEquals(setOf("group-1", "group-2"), viewModel.uiState.value.expandedGroupIds)

        viewModel.onAction(MainAction.CollapseAll)

        assertEquals(emptySet<String>(), viewModel.uiState.value.expandedGroupIds)
    }

    @Test
    fun deleteSubscriptionRemovesItThroughTheDataSource() {
        val dataSource = newDataSource()
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.DeleteSubscription("sub-1"))

        runBlocking { verify(dataSource, timeout(5_000)).removeSubscription("sub-1") }
    }

    @Test
    fun importClipboardEmptyEmptiesSnackbarAndSkipsImport() {
        val dataSource = newDataSource()
        runBlocking { whenever(dataSource.getClipboardContent()).thenReturn("   ") }
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.ImportClipboard)
        val event = runBlocking {
            withTimeoutOrNull(5_000) { viewModel.snackbarEvent.first() }
        }
        assertEquals("str-${R.string.shroxy_clipboard_empty}", event)
        runBlocking {
            verify(dataSource, never()).importBatchConfig(any<String>(), any<String>(), any<Boolean>())
        }
    }

    @Test
    fun importClipboardFailureLogsAndEmitsFailureSnackbar() {
        val dataSource = newDataSource()
        runBlocking {
            whenever(dataSource.getClipboardContent()).thenReturn("vmess://config")
            whenever(dataSource.importBatchConfig(any<String>(), any<String>(), any<Boolean>()))
                .thenThrow(RuntimeException("boom"))
        }
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.ImportClipboard)
        val event = runBlocking {
            withTimeoutOrNull(5_000) { viewModel.snackbarEvent.first() }
        }
        assertEquals("str-${R.string.toast_failure}", event)
    }

    @Test
    fun importClipboardSuccessWithZeroImportsEmitsFailureSnackbar() {
        val dataSource = newDataSource()
        runBlocking {
            whenever(dataSource.getClipboardContent()).thenReturn("not-a-config")
            whenever(dataSource.importBatchConfig(any<String>(), any<String>(), any<Boolean>()))
                .thenReturn(0 to 0)
        }
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.ImportClipboard)
        val event = runBlocking {
            withTimeoutOrNull(5_000) { viewModel.snackbarEvent.first() }
        }
        assertNotEquals("str-${R.string.shroxy_clipboard_empty}", event)
        assertEquals("str-${R.string.toast_failure}", event)
    }

    @Test
    fun importClipboardSuccessEmitsCountSnackbar() {
        val dataSource = newDataSource()
        runBlocking {
            whenever(dataSource.getString(any<Int>(), any<Array<Any>>())).thenReturn("imported")
            whenever(dataSource.getClipboardContent()).thenReturn("vmess://config")
            whenever(dataSource.importBatchConfig(any<String>(), any<String>(), any<Boolean>()))
                .thenReturn(2 to 0)
        }
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.ImportClipboard)
        val event = runBlocking {
            withTimeoutOrNull(5_000) { viewModel.snackbarEvent.first() }
        }
        assertEquals("imported", event)
    }

    @Test
    fun testSelectedServerDoesNothingWhenServiceIsNotRunning() {
        val dataSource = newDataSource()
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.TestSelectedServer)

        assertEquals(false, viewModel.uiState.value.isTesting)
        assertNull(viewModel.uiState.value.status as? MainStatus.Testing)
    }

    @Test
    fun setBypassRuPersistsKeyAndRestartsWhenRunning() {
        val dataSource = newDataSource()
        runBlocking {
            whenever(dataSource.mainServiceEvent)
                .thenReturn(MutableStateFlow(MainServiceEvent.StateRunning))
        }
        val viewModel = mockStatic(android.os.SystemClock::class.java).use { clock ->
            clock.`when`<Long> { android.os.SystemClock.elapsedRealtime() }.thenReturn(0L)
            newViewModel(dataSource)
        }
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.onAction(MainAction.SetBypassRu(true))

        assertTrue(viewModel.uiState.value.bypassRu)
        verify(MmkvTestStorage.settings)
            .encode(eq(AppConfig.PREF_SHROXY_BYPASS_RU), eq(true))
        verify(dataSource).sendMsg2Service(AppConfig.MSG_STATE_RESTART, "")
    }

    @Test
    fun setBypassRuPersistsKeyWithoutRestartWhenIdle() {
        val dataSource = newDataSource()
        val viewModel = newViewModel(dataSource)
        assertFalse(viewModel.uiState.value.isRunning)

        viewModel.onAction(MainAction.SetBypassRu(false))

        assertFalse(viewModel.uiState.value.bypassRu)
        verify(MmkvTestStorage.settings)
            .encode(eq(AppConfig.PREF_SHROXY_BYPASS_RU), eq(false))
        verify(dataSource, never()).sendMsg2Service(any<Int>(), any<String>())
    }
}
