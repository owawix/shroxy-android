package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget

/** Locale-neutral state formatted only when it reaches the main UI. */
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    /** Group ID of the running bulk ping test; null = no group test is running. */
    val testingGroupId: String? = null,
    val status: MainStatus = MainStatus.Disconnected,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    /** IDs of subscription groups whose server list is expanded (empty = all collapsed). */
    val expandedGroupIds: Set<String> = emptySet(),
    /** Shroxy bypass-RU routing mode (PREF_SHROXY_BYPASS_RU); false = global proxy mode. */
    val bypassRu: Boolean = false,
    /** A clipboard/QR/batch import is running; the Shroxy screen shows the skeleton card. */
    val importInProgress: Boolean = false,
    /** Shroxy "Edit subscription" dialog; null = closed. Keyed by subscription GUID. */
    val subscriptionEditDialog: SubscriptionEditUiState? = null,
    /**
     * Persistent Shroxy HWID shown only while no subscription exists; null = not yet
     * provisioned (generated lazily on the first empty-state render, never at a normal
     * start with subscriptions).
     */
    val hwid: String? = null,
)

/**
 * State of the Shroxy "Edit subscription" dialog. Identified by the subscription GUID
 * (never a list position); the field values seed the dialog's transient text input and
 * the validation error is a durable state owned by the ViewModel.
 */
data class SubscriptionEditUiState(
    val subscriptionGuid: String,
    val subscriptionUrl: String,
    val userAgent: String,
    val requestHeadersJson: String,
    /** Localized headers-JSON validation error; null = no error. */
    val headersError: String? = null,
)

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object TestCurrentServer : MainAction
    data object TestSelectedServer : MainAction
    data class TestAllServers(val groupId: String) : MainAction
    data object TestRealAllServers : MainAction
    data object CollapseAll : MainAction
    data class ToggleGroupExpanded(val groupId: String, val expanded: Boolean) : MainAction
    data class RefreshSubscription(val subscriptionGuid: String) : MainAction
    data class DeleteSubscription(val subscriptionGuid: String) : MainAction
    /** Opens the Shroxy "Edit subscription" dialog for the subscription with this GUID. */
    data class EditSubscription(val subscriptionGuid: String) : MainAction
    /**
     * Persists the edited subscription URL/User-Agent/headers JSON for this GUID, closes
     * the dialog on success and triggers a subscription refresh; invalid headers JSON
     * keeps the dialog open with an inline error instead of persisting.
     */
    data class UpdateSubscriptionSettings(
        val subscriptionGuid: String,
        val url: String,
        val userAgent: String,
        val headersJson: String,
    ) : MainAction
    /** Closes the Shroxy "Edit subscription" dialog without persisting. */
    data object DismissSubscriptionEditor : MainAction
    data object OpenQr : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data class UpdateGroupSubscription(val groupId: String) : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.v2ray.ang.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data class OpenSubscriptionLink(val url: String) : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data object LocateHandled : MainAction

    /** Toggle Shroxy bypass-RU routing; restarts the core when the service is running. */
    data class SetBypassRu(val enabled: Boolean) : MainAction

    /**
     * Copies the persistent Shroxy HWID (shown in the empty state) to the clipboard and
     * confirms with a snackbar. Carries the value from the rendered state so the handler
     * copies exactly what the user saw.
     */
    data class CopyHwid(val hwid: String) : MainAction
}
