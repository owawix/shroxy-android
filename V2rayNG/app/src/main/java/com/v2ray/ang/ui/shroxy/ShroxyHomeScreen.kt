package com.v2ray.ang.ui.shroxy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainStatus
import com.v2ray.ang.ui.main.MainUiState
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.shroxy.components.ShroxyAddServerDropdown
import com.v2ray.ang.ui.shroxy.components.ShroxyPingUiState
import com.v2ray.ang.ui.shroxy.components.ShroxyPowerButton
import com.v2ray.ang.ui.shroxy.components.ShroxyPowerState
import com.v2ray.ang.ui.shroxy.components.ShroxyServerRow
import com.v2ray.ang.ui.shroxy.components.ShroxySubscriptionGroup
import com.v2ray.ang.ui.shroxy.components.ShroxyTestPingRow
import com.v2ray.ang.ui.shroxy.components.ShroxyTopBar

/**
 * Shroxy main screen: floating top bar, power button, test/ping row, content zone
 * (empty state or subscription group cards) and two bottom pill buttons.
 * All user operations are dispatched through [MainAction]/[MainViewModel].
 */
@Composable
fun ShroxyHomeScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPurchaseLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val sessionSeconds by mainViewModel.sessionSeconds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mainViewModel) {
        mainViewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val groups = uiState.groups
    val powerState = when {
        uiState.isTesting && uiState.isRunning -> ShroxyPowerState.TestRunning
        uiState.isRunning -> ShroxyPowerState.Connected
        else -> ShroxyPowerState.Idle
    }
    val lastTestMillis = (uiState.status as? MainStatus.ConnectionTest)?.result?.delayMillis
    val pingState = pingUiState(uiState.isTesting, lastTestMillis)
    val groupMenuExpanded = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var addMenuExpanded by rememberSaveable { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                ShroxyTopBar(
                    onSettingsClick = onOpenSettings,
                    onAddClick = { addMenuExpanded = true },
                    modifier = Modifier.padding(top = 16.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp),
                ) {
                    ShroxyAddServerDropdown(
                        expanded = addMenuExpanded,
                        onDismiss = { addMenuExpanded = false },
                        onAddFromClipboard = { onAction(MainAction.ImportClipboard) },
                        onScanQr = { onAction(MainAction.OpenQr) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            ShroxyPowerButton(
                state = powerState,
                sessionSeconds = sessionSeconds,
                onClick = { onAction(MainAction.ToggleService) },
            )
            Spacer(modifier = Modifier.height(24.dp))
            ShroxyTestPingRow(
                state = pingState,
                hasSubscriptions = groups.isNotEmpty(),
                onTestClick = { onAction(MainAction.TestSelectedServer) },
                onCollapseAllClick = { onAction(MainAction.CollapseAll) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (showEmptyState(groups.size)) {
                    ShroxyEmptyState(onOpenPurchaseLink = onOpenPurchaseLink)
                } else {
                    ShroxyGroupList(
                        mainViewModel = mainViewModel,
                        uiState = uiState,
                        groupMenuExpanded = groupMenuExpanded,
                        onAction = onAction,
                    )
                }
            }
            ShroxyBottomPills(onAction = onAction)
        }
    }
}

@Composable
private fun ShroxyGroupList(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    groupMenuExpanded: MutableMap<String, Boolean>,
    onAction: (MainAction) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = uiState.groups, key = { it.id }) { group ->
            val groupState by mainViewModel.serverGroupState(group.id)
                .collectAsStateWithLifecycle()
            val expanded = group.id in uiState.expandedGroupIds
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                ShroxySubscriptionGroup(
                    title = group.remarks,
                    updatedMillis = group.lastUpdated.takeIf { it > 0 },
                    trafficUsedBytes = group.usageInfo?.used,
                    trafficTotalBytes = group.usageInfo?.total,
                    expanded = expanded,
                    menuExpanded = groupMenuExpanded[group.id] == true,
                    onToggleExpand = {
                        onAction(MainAction.ToggleGroupExpanded(group.id, !expanded))
                    },
                    onRefresh = { onAction(MainAction.RefreshSubscription(group.id)) },
                    onTestAll = { onAction(MainAction.TestAllServers(group.id)) },
                    onMenuExpandedChange = { groupMenuExpanded[group.id] = it },
                    onUpdate = { onAction(MainAction.RefreshSubscription(group.id)) },
                    onDelete = { onAction(MainAction.DeleteSubscription(group.id)) },
                )
                if (expanded) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        groupState.rows.forEach { row ->
                            key(row.guid) {
                                ShroxyServerRow(
                                    serverGuid = row.guid,
                                    title = row.remarks,
                                    subtitle = row.typeDescription,
                                    remark = row.remarks,
                                    country = row.remarks,
                                    selected = row.guid == uiState.selectedGuid,
                                    onClick = { guid -> onAction(MainAction.SelectServer(guid)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShroxyEmptyState(onOpenPurchaseLink: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_subscriptions),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.buy_subscription_prefix),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.buy_subscription_link),
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable(onClick = onOpenPurchaseLink),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun ShroxyBottomPills(onAction: (MainAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShroxyPillButton(
            icon = Icons.Outlined.ContentPaste,
            label = stringResource(R.string.from_clipboard),
            onClick = { onAction(MainAction.ImportClipboard) },
            modifier = Modifier.weight(1f),
        )
        ShroxyPillButton(
            icon = Icons.Outlined.QrCodeScanner,
            label = stringResource(R.string.qr_code),
            onClick = { onAction(MainAction.OpenQr) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShroxyPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12, heightDp = 917, widthDp = 412)
@Composable
private fun ShroxyHomeScreenEmptyPreview() {
    AppTheme {
        // Static preview of the empty state chrome; live state needs a ViewModel.
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShroxyTopBar(onSettingsClick = {}, onAddClick = {}, modifier = Modifier.padding(top = 16.dp))
                Spacer(modifier = Modifier.height(24.dp))
                ShroxyPowerButton(
                    state = ShroxyPowerState.Idle,
                    sessionSeconds = 0,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(24.dp))
                ShroxyTestPingRow(
                    state = ShroxyPingUiState.Idle,
                    hasSubscriptions = false,
                    onTestClick = {},
                    onCollapseAllClick = {},
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    ShroxyEmptyState(onOpenPurchaseLink = {})
                }
                ShroxyBottomPills(onAction = {})
            }
        }
    }
}
