package com.v2ray.ang.ui.main

import android.app.Application
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvTestStorage
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MainSubscriptionEditTest {

    companion object {
        @JvmStatic
        @BeforeAll
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun setUpClass() {
            MmkvTestStorage.initialize()
            // Route LogUtil to the "off" level so unit tests never touch android.util.Log
            // (the invalid-JSON path logs through JsonUtil on a Dispatchers.IO thread).
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
        whenever(dataSource.mainServiceEvent).thenReturn(emptyFlow())
        whenever(dataSource.getSelectedSubscriptionId()).thenReturn("")
        whenever(dataSource.getSelectServer()).thenReturn(null)
        whenever(dataSource.getConfirmRemove()).thenReturn(false)
        whenever(dataSource.getDoubleColumnDisplay()).thenReturn(false)
        whenever(dataSource.getSubscriptions()).thenReturn(emptyList())
        whenever(dataSource.getString(any<Int>())).thenAnswer { "str-${it.getArgument<Int>(0)}" }
        return dataSource
    }

    private fun awaitTrue(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(true, condition())
    }

    // ---------- JSON validator ----------

    @Test
    fun parseSubscriptionHeadersValidObjectReturnsMap() {
        assertEquals(
            linkedMapOf("X-hwid" to "my_test_device", "Authorization" to "Bearer test"),
            parseSubscriptionHeaders("""{"X-hwid":"my_test_device","Authorization":"Bearer test"}"""),
        )
    }

    @Test
    fun parseSubscriptionHeadersInvalidSyntaxReturnsNull() {
        assertNull(parseSubscriptionHeaders("""{"X-hwid": """))
        assertNull(parseSubscriptionHeaders("not json at all"))
        // A valid JSON document that is not an object cannot carry headers.
        assertNull(parseSubscriptionHeaders("""["X-hwid"]"""))
    }

    @Test
    fun parseSubscriptionHeadersRejectsLenientOnlySyntax() {
        // Gson's lenient parser accepts an unquoted value and would persist and send
        // the literal wire header "X-hwid: broken"; validation must reject it.
        assertNull(parseSubscriptionHeaders("""{"X-hwid": broken}"""))
        // Unquoted name.
        assertNull(parseSubscriptionHeaders("""{X-hwid: "v"}"""))
        // Trailing content after the object is not exhausted input.
        assertNull(parseSubscriptionHeaders("""{"X-hwid":"v"} trailing"""))
    }

    @Test
    fun parseSubscriptionHeadersAcceptsStrictObjectAndEmptyObject() {
        assertEquals(
            linkedMapOf("X-hwid" to "d", "Authorization" to "Bearer t"),
            parseSubscriptionHeaders("""{"X-hwid":"d","Authorization":"Bearer t"}"""),
        )
        assertEquals(emptyMap<String, String>(), parseSubscriptionHeaders("{}"))
    }

    @Test
    fun parseSubscriptionHeadersBlankClearsHeaders() {
        assertEquals(emptyMap<String, String>(), parseSubscriptionHeaders(""))
        assertEquals(emptyMap<String, String>(), parseSubscriptionHeaders("   \n "))
        assertEquals(emptyMap<String, String>(), parseSubscriptionHeaders(null))
    }

    @Test
    fun parseSubscriptionHeadersKeepsExplicitBlankValues() {
        // Blank values are explicit user input: they are kept (and persisted), not
        // silently turned into a "clear headers" request.
        assertEquals(
            linkedMapOf("X-hwid" to "  "),
            parseSubscriptionHeaders("""{"X-hwid":"  "}"""),
        )
        // JSON null entries carry no sendable value and are dropped by the parser.
        assertEquals(
            linkedMapOf("X-keep" to "v"),
            parseSubscriptionHeaders("""{"X-gone":null,"X-keep":"v"}"""),
        )
    }

    // ---------- Action mapping ----------

    @Test
    fun editSubscriptionOpensDialogSeededByGuid() {
        val dataSource = newDataSource()
        whenever(dataSource.getSubscriptionItem("sub-1")).thenReturn(
            SubscriptionItem(
                url = "https://example.com/sub",
                userAgent = "OldAgent/1.0",
                requestHeaders = """{"A":"b"}""",
            )
        )
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.EditSubscription("sub-1"))

        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog != null }
        val dialog = viewModel.uiState.value.subscriptionEditDialog
        assertEquals("sub-1", dialog?.subscriptionGuid)
        assertEquals("https://example.com/sub", dialog?.subscriptionUrl)
        assertEquals("OldAgent/1.0", dialog?.userAgent)
        assertEquals("""{"A":"b"}""", dialog?.requestHeadersJson)
        assertNull(dialog?.headersError)
    }

    @Test
    fun editSubscriptionMissingItemKeepsDialogClosed() {
        val dataSource = newDataSource()
        whenever(dataSource.getSubscriptionItem("missing-guid")).thenReturn(null)
        val viewModel = newViewModel(dataSource)

        viewModel.onAction(MainAction.EditSubscription("missing-guid"))

        // Give the handler coroutine time to run; no dialog may appear for a missing item.
        Thread.sleep(100)
        assertNull(viewModel.uiState.value.subscriptionEditDialog)
    }

    @Test
    fun updateSubscriptionSettingsInvalidJsonShowsInlineErrorWithoutPersisting() {
        val dataSource = newDataSource()
        whenever(dataSource.getSubscriptionItem("sub-1")).thenReturn(SubscriptionItem())
        val viewModel = newViewModel(dataSource)
        viewModel.onAction(MainAction.EditSubscription("sub-1"))
        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog != null }

        viewModel.onAction(
            MainAction.UpdateSubscriptionSettings(
                subscriptionGuid = "sub-1",
                url = "https://example.com/sub",
                userAgent = "",
                headersJson = """{"X-hwid": """,
            )
        )

        awaitTrue(5_000) {
            viewModel.uiState.value.subscriptionEditDialog?.headersError ==
                "str-${R.string.shroxy_sub_edit_headers_error}"
        }
        assertEquals("sub-1", viewModel.uiState.value.subscriptionEditDialog?.subscriptionGuid)
        verify(dataSource, never()).encodeSubscription(any(), any())
        verify(dataSource, never()).updateConfigViaSub(any())
    }

    @Test
    fun updateSubscriptionSettingsPersistsByGuidClosesDialogAndRefreshes() {
        val dataSource = newDataSource()
        val item = SubscriptionItem(
            url = "https://example.com/sub",
            userAgent = "OldAgent/1.0",
            requestHeaders = """{"A":"b"}""",
        )
        whenever(dataSource.getSubscriptionItem("sub-1")).thenReturn(item)
        // Stop the follow-up refresh before its result toasts (Android Toast is not
        // available in JVM tests); CancellationException ends the coroutine silently.
        whenever(dataSource.updateConfigViaSub(any()))
            .thenThrow(CancellationException("refresh stopped in test"))
        val viewModel = newViewModel(dataSource)
        viewModel.onAction(MainAction.EditSubscription("sub-1"))
        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog != null }

        viewModel.onAction(
            MainAction.UpdateSubscriptionSettings(
                subscriptionGuid = "sub-1",
                url = "  https://example.com/sub\n",
                userAgent = "   ",
                headersJson = """{"X-hwid":"my_test_device","Authorization":"Bearer test"}""",
            )
        )

        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog == null }
        val itemCaptor = argumentCaptor<SubscriptionItem>()
        verify(dataSource, timeout(5_000)).encodeSubscription(eq("sub-1"), itemCaptor.capture())
        assertEquals("https://example.com/sub", itemCaptor.firstValue.url)
        // Blank User-Agent clears the per-subscription override (default Shroxy UA).
        assertNull(itemCaptor.firstValue.userAgent)
        assertEquals(
            """{"X-hwid":"my_test_device","Authorization":"Bearer test"}""",
            itemCaptor.firstValue.requestHeaders,
        )
        // A successful save triggers the existing subscription refresh for the same GUID.
        val cacheCaptor = argumentCaptor<SubscriptionCache>()
        verify(dataSource, timeout(5_000)).updateConfigViaSub(cacheCaptor.capture())
        assertEquals("sub-1", cacheCaptor.firstValue.guid)
    }

    @Test
    fun updateSubscriptionSettingsEmptyHeadersClearPersistedHeaders() {
        val dataSource = newDataSource()
        val item = SubscriptionItem(
            url = "https://example.com/sub",
            userAgent = "MyAgent/2.0",
            requestHeaders = """{"A":"b"}""",
        )
        whenever(dataSource.getSubscriptionItem("sub-1")).thenReturn(item)
        whenever(dataSource.updateConfigViaSub(any()))
            .thenThrow(CancellationException("refresh stopped in test"))
        val viewModel = newViewModel(dataSource)
        viewModel.onAction(MainAction.EditSubscription("sub-1"))
        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog != null }

        viewModel.onAction(
            MainAction.UpdateSubscriptionSettings(
                subscriptionGuid = "sub-1",
                url = "https://example.com/sub",
                userAgent = "MyAgent/2.0",
                headersJson = "  ",
            )
        )

        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog == null }
        val itemCaptor = argumentCaptor<SubscriptionItem>()
        verify(dataSource, timeout(5_000)).encodeSubscription(eq("sub-1"), itemCaptor.capture())
        assertNull(itemCaptor.firstValue.requestHeaders)
        assertEquals("MyAgent/2.0", itemCaptor.firstValue.userAgent)
    }

    @Test
    fun updateSubscriptionSettingsBlankValuedHeadersPersistInsteadOfClearing() {
        val dataSource = newDataSource()
        val item = SubscriptionItem(
            url = "https://example.com/sub",
            requestHeaders = """{"A":"b"}""",
        )
        whenever(dataSource.getSubscriptionItem("sub-1")).thenReturn(item)
        whenever(dataSource.updateConfigViaSub(any()))
            .thenThrow(CancellationException("refresh stopped in test"))
        val viewModel = newViewModel(dataSource)
        viewModel.onAction(MainAction.EditSubscription("sub-1"))
        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog != null }

        viewModel.onAction(
            MainAction.UpdateSubscriptionSettings(
                subscriptionGuid = "sub-1",
                url = "https://example.com/sub",
                userAgent = "",
                headersJson = """{"X-hwid":"  "}""",
            )
        )

        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog == null }
        val itemCaptor = argumentCaptor<SubscriptionItem>()
        verify(dataSource, timeout(5_000)).encodeSubscription(eq("sub-1"), itemCaptor.capture())
        // Non-blank JSON is explicit input: it persists as entered instead of clearing.
        assertEquals("""{"X-hwid":"  "}""", itemCaptor.firstValue.requestHeaders)
    }

    @Test
    fun updateSubscriptionSettingsEmptyObjectPersistsInsteadOfClearing() {
        val dataSource = newDataSource()
        val item = SubscriptionItem(
            url = "https://example.com/sub",
            requestHeaders = """{"A":"b"}""",
        )
        whenever(dataSource.getSubscriptionItem("sub-1")).thenReturn(item)
        whenever(dataSource.updateConfigViaSub(any()))
            .thenThrow(CancellationException("refresh stopped in test"))
        val viewModel = newViewModel(dataSource)
        viewModel.onAction(MainAction.EditSubscription("sub-1"))
        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog != null }

        viewModel.onAction(
            MainAction.UpdateSubscriptionSettings(
                subscriptionGuid = "sub-1",
                url = "https://example.com/sub",
                userAgent = "",
                headersJson = "{}",
            )
        )

        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog == null }
        val itemCaptor = argumentCaptor<SubscriptionItem>()
        verify(dataSource, timeout(5_000)).encodeSubscription(eq("sub-1"), itemCaptor.capture())
        // Only a blank field clears; an explicit empty object persists as entered.
        assertEquals("{}", itemCaptor.firstValue.requestHeaders)
    }

    @Test
    fun dismissSubscriptionEditorClosesDialogWithoutPersisting() {
        val dataSource = newDataSource()
        whenever(dataSource.getSubscriptionItem("sub-1")).thenReturn(SubscriptionItem())
        val viewModel = newViewModel(dataSource)
        viewModel.onAction(MainAction.EditSubscription("sub-1"))
        awaitTrue(5_000) { viewModel.uiState.value.subscriptionEditDialog != null }

        viewModel.onAction(MainAction.DismissSubscriptionEditor)

        assertNull(viewModel.uiState.value.subscriptionEditDialog)
        assertFalse(viewModel.uiState.value.importInProgress)
        verify(dataSource, never()).encodeSubscription(any(), any())
    }
}
