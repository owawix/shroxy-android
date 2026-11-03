package com.v2ray.ang.ui.main

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvTestStorage
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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
    fun importSetsInProgressFlagAndClearsItAfterImport() {
        val dataSource = newDataSource()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        runBlocking {
            whenever(dataSource.getClipboardContent()).thenReturn("vmess://config")
            whenever(dataSource.importBatchConfig(any<String>(), any<String>(), any<Boolean>()))
                .thenAnswer {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    1 to 0
                }
        }
        val viewModel = newViewModel(dataSource)
        assertFalse(viewModel.uiState.value.importInProgress)

        viewModel.onAction(MainAction.ImportClipboard)

        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(viewModel.uiState.value.importInProgress)
        release.countDown()
        awaitTrue(5_000) { !viewModel.uiState.value.importInProgress }
        assertFalse(viewModel.uiState.value.importInProgress)
    }

    @Test
    fun hostResumeSkipsGroupReloadWhileImportIsInProgress() {
        val dataSource = newDataSource()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        runBlocking {
            whenever(dataSource.ensureHwid()).thenReturn("stubhwid0000001")
            whenever(dataSource.getClipboardContent()).thenReturn("vmess://config")
            whenever(dataSource.importBatchConfig(any<String>(), any<String>(), any<Boolean>()))
                .thenAnswer {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    1 to 0
                }
            whenever(dataSource.getString(any<Int>(), any<Array<Any>>())).thenReturn("imported")
        }
        val viewModel = newViewModel(dataSource)
        // The initial group pass has completed before the import starts.
        awaitTrue(5_000) { viewModel.uiState.value.hwid == "stubhwid0000001" }

        viewModel.onAction(MainAction.ImportClipboard)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(viewModel.uiState.value.importInProgress)

        // A resume while the import runs must not dispatch a competing reload; the
        // import path refreshes the groups itself on completion.
        viewModel.onHostResumed()
        // Give any (incorrect) reload dispatched on Dispatchers.IO time to surface.
        Thread.sleep(200)
        runBlocking { verify(dataSource, times(1)).getSubscriptions() }

        release.countDown()
        awaitTrue(5_000) { !viewModel.uiState.value.importInProgress }
        runBlocking { verify(dataSource, timeout(5_000).times(2)).getSubscriptions() }
    }

    @Test
    fun importBatchConfigSetsInProgressFlagAndClearsItAfterImport() {
        val dataSource = newDataSource()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        runBlocking {
            whenever(dataSource.importBatchConfig(any<String>(), any<String>(), any<Boolean>()))
                .thenAnswer {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    1 to 0
                }
        }
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.ImportBatchConfig("vmess://config"))

        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(viewModel.uiState.value.importInProgress)
        release.countDown()
        awaitTrue(5_000) { !viewModel.uiState.value.importInProgress }
        assertFalse(viewModel.uiState.value.importInProgress)
    }

    @Test
    fun importFailureClearsInProgressFlag() {
        val dataSource = newDataSource()
        runBlocking {
            whenever(dataSource.getClipboardContent()).thenReturn("vmess://config")
            whenever(dataSource.importBatchConfig(any<String>(), any<String>(), any<Boolean>()))
                .thenThrow(RuntimeException("boom"))
        }
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.ImportClipboard)

        awaitTrue(5_000) { !viewModel.uiState.value.importInProgress }
        assertFalse(viewModel.uiState.value.importInProgress)
    }

    @Test
    fun secondImportWhileInProgressIsDroppedAndEmitsInProgressSnackbar() {
        val dataSource = newDataSource()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        runBlocking {
            whenever(dataSource.getClipboardContent()).thenReturn("vmess://first")
            whenever(dataSource.importBatchConfig(any<String>(), any<String>(), any<Boolean>()))
                .thenAnswer {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    1 to 0
                }
        }
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.ImportClipboard)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        viewModel.onAction(MainAction.ImportBatchConfig("vmess://second"))

        val event = runBlocking {
            withTimeoutOrNull(5_000) { viewModel.snackbarEvent.first() }
        }
        assertEquals("str-${R.string.shroxy_import_in_progress}", event)
        release.countDown()
        runBlocking {
            verify(dataSource, timeout(5_000)).importBatchConfig(any<String>(), any<String>(), any<Boolean>())
        }
        awaitTrue(5_000) { !viewModel.uiState.value.importInProgress }
        // The second request was dropped: exactly one import reached the data source.
        runBlocking {
            verify(dataSource, times(1)).importBatchConfig(any<String>(), any<String>(), any<Boolean>())
        }
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
    fun testAllServersResolvesEveryServerGuidOfTheGroupFromStorage() {
        val dataSource = newDataSource()
        runBlocking {
            whenever(dataSource.getServerGuidList("group-1")).thenReturn(listOf("g1", "g2", "g3"))
            whenever(dataSource.getServerGuidList("group-2")).thenReturn(listOf("x1"))
        }
        val viewModel = newViewModel(dataSource)

        // The group was never expanded: its ServerGroupUiState is empty. The speed
        // action must still measure every server of the subscription (storage-based).
        viewModel.onAction(MainAction.TestAllServers("group-1"))

        val messageCaptor = argumentCaptor<TestServiceMessage>()
        runBlocking {
            verify(dataSource, timeout(5_000)).clearAllTestDelayResults(listOf("g1", "g2", "g3"))
            verify(dataSource, timeout(5_000)).sendMsg2TestService(messageCaptor.capture(), anyOrNull())
        }
        assertEquals(AppConfig.MSG_MEASURE_CONFIG_START, messageCaptor.firstValue.key)
        assertEquals("group-1", messageCaptor.firstValue.subscriptionId)
        assertEquals(true, messageCaptor.firstValue.onlyTcp)
        // No keyword filter: the service expands the whole subscription list itself.
        assertEquals(emptyList<String>(), messageCaptor.firstValue.serverGuids)
        runBlocking {
            verify(dataSource, never()).clearAllTestDelayResults(listOf("x1"))
        }
        assertEquals(true, viewModel.uiState.value.isTesting)
    }

    @Test
    fun testAllServersDelaysSurfacePerRowByGuidAndLeaveOtherGroupsAlone() {
        val dataSource = newDataSource()
        val events = MutableSharedFlow<MainServiceEvent>(extraBufferCapacity = 16)
        // Emulates CoreTestService persisting each result before the batch finishes,
        // so the post-finish group reload reads the same delays back.
        val persistedDelays = mutableMapOf<String, Long>()
        runBlocking {
            whenever(dataSource.mainServiceEvent).thenReturn(events)
            whenever(dataSource.getSubscriptions()).thenReturn(
                listOf(subCache("group-1"), subCache("group-2"))
            )
            whenever(dataSource.getServerGuidList("group-1")).thenReturn(listOf("g1", "g2"))
            whenever(dataSource.getServerGuidList("group-2")).thenReturn(listOf("x1"))
            listOf("g1", "g2", "x1").forEach { guid ->
                whenever(dataSource.decodeServerConfig(guid)).thenReturn(
                    ProfileItem(configType = EConfigType.VLESS, remarks = guid)
                        .apply { description = "desc-$guid" }
                )
                whenever(dataSource.decodeAffiliationInfo(guid)).thenAnswer {
                    persistedDelays[guid]?.let { aff -> ServerAffiliationInfo(testDelayMillis = aff) }
                }
            }
        }
        val viewModel = newViewModel(dataSource)
        awaitRows(viewModel, "group-1", 2)
        awaitRows(viewModel, "group-2", 1)

        var requestId: String? = null
        runBlocking {
            whenever(dataSource.sendMsg2TestService(any(), anyOrNull())).thenAnswer {
                requestId = it.getArgument(1)
            }
        }
        viewModel.onAction(MainAction.TestAllServers("group-1"))
        awaitTrue(5_000) { requestId != null }
        val id = requestId.orEmpty()

        // Same delivery contract as CoreTestService: one success per server, then finish.
        persistedDelays["g1"] = 120L
        events.tryEmit(MainServiceEvent.MeasureConfigSuccess(RealPingResult("g1", 120L), id))
        persistedDelays["g2"] = 250L
        events.tryEmit(MainServiceEvent.MeasureConfigSuccess(RealPingResult("g2", 250L), id))
        events.tryEmit(MainServiceEvent.MeasureConfigFinish(id))

        awaitTrue(5_000) {
            viewModel.serverGroupState("group-1").value.rows
                .firstOrNull { it.guid == "g1" }?.testDelayMillis == 120L
        }
        val rows = viewModel.serverGroupState("group-1").value.rows
        assertEquals(120L, rows.first { it.guid == "g1" }.testDelayMillis)
        assertEquals(250L, rows.first { it.guid == "g2" }.testDelayMillis)
        // The other group is untouched by this batch.
        assertEquals(0L, viewModel.serverGroupState("group-2").value.rows
            .first { it.guid == "x1" }.testDelayMillis)
    }

    private fun subCache(guid: String) =
        SubscriptionCache(guid, SubscriptionItem(remarks = guid))

    /** Stubs a one-group data source that can run a bulk ping test end to end. */
    private fun stubBulkTestDataSource(
        dataSource: MainDataSource,
        events: MutableSharedFlow<MainServiceEvent>,
    ) {
        runBlocking {
            whenever(dataSource.mainServiceEvent).thenReturn(events)
            whenever(dataSource.getSubscriptions()).thenReturn(listOf(subCache("group-1")))
            whenever(dataSource.getServerGuidList("group-1")).thenReturn(listOf("g1", "g2"))
            listOf("g1", "g2").forEach { guid ->
                whenever(dataSource.decodeServerConfig(guid)).thenReturn(
                    ProfileItem(configType = EConfigType.VLESS, remarks = guid)
                )
            }
        }
    }

    /** Starts a bulk test and waits until the service message carries its request ID. */
    private fun startBulkTest(dataSource: MainDataSource, viewModel: MainViewModel): String {
        var requestId: String? = null
        runBlocking {
            whenever(dataSource.sendMsg2TestService(any(), anyOrNull())).thenAnswer {
                requestId = it.getArgument(1)
            }
        }
        viewModel.onAction(MainAction.TestAllServers("group-1"))
        awaitTrue(5_000) { requestId != null }
        return requestId.orEmpty()
    }

    @Test
    fun bulkTestSetsTestingGroupIdAndClearsItWhenFinished() {
        val dataSource = newDataSource()
        val events = MutableSharedFlow<MainServiceEvent>(extraBufferCapacity = 16)
        stubBulkTestDataSource(dataSource, events)
        val viewModel = newViewModel(dataSource)
        awaitRows(viewModel, "group-1", 2)
        assertNull(viewModel.uiState.value.testingGroupId)

        val id = startBulkTest(dataSource, viewModel)
        assertEquals("group-1", viewModel.uiState.value.testingGroupId)
        assertTrue(viewModel.uiState.value.isTesting)

        events.tryEmit(MainServiceEvent.MeasureConfigFinish(id))
        awaitTrue(5_000) { viewModel.uiState.value.testingGroupId == null }
        assertFalse(viewModel.uiState.value.isTesting)
    }

    @Test
    fun bulkTestClearsTestingGroupIdWhenTheServiceCancelsTheBatch() {
        val dataSource = newDataSource()
        val events = MutableSharedFlow<MainServiceEvent>(extraBufferCapacity = 16)
        stubBulkTestDataSource(dataSource, events)
        val viewModel = newViewModel(dataSource)
        awaitRows(viewModel, "group-1", 2)

        val id = startBulkTest(dataSource, viewModel)
        assertEquals("group-1", viewModel.uiState.value.testingGroupId)

        events.tryEmit(MainServiceEvent.MeasureConfigCancelled(id))
        awaitTrue(5_000) { viewModel.uiState.value.testingGroupId == null }
        assertFalse(viewModel.uiState.value.isTesting)
    }

    @Test
    fun cancelAllPingClearsTestingGroupId() {
        val dataSource = newDataSource()
        val events = MutableSharedFlow<MainServiceEvent>(extraBufferCapacity = 16)
        stubBulkTestDataSource(dataSource, events)
        val viewModel = newViewModel(dataSource)
        awaitRows(viewModel, "group-1", 2)

        startBulkTest(dataSource, viewModel)
        assertEquals("group-1", viewModel.uiState.value.testingGroupId)

        viewModel.onAction(MainAction.CancelTesting)
        assertNull(viewModel.uiState.value.testingGroupId)
        assertFalse(viewModel.uiState.value.isTesting)
    }

    private fun awaitRows(viewModel: MainViewModel, groupId: String, size: Int) {
        awaitTrue(5_000) { viewModel.serverGroupState(groupId).value.rows.size == size }
        assertEquals(size, viewModel.serverGroupState(groupId).value.rows.size)
    }

    private fun awaitTrue(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
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
