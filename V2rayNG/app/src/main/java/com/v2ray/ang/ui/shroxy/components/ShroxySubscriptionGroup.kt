package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Subscription group card: header (chevron, title, last-update timestamp, refresh / test-all /
 * overflow actions) plus a traffic info strip. Fully stateless — expansion and overflow-menu
 * visibility are hoisted by the caller.
 */
@Composable
fun ShroxySubscriptionGroup(
    title: String,
    updatedMillis: Long?,
    trafficUsedBytes: Long?,
    trafficTotalBytes: Long?,
    expanded: Boolean,
    hasRows: Boolean,
    menuExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onTestAll: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        // Expanded with rows: server rows continue below the card, so the bottom edge is
        // square. Collapsed or expanded with no rows (loading / filter no-match): the
        // traffic strip is the last element, so the bottom stays rounded.
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (expanded && hasRows) 0.dp else 16.dp,
            bottomEnd = if (expanded && hasRows) 0.dp else 16.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            GroupHeader(
                title = title,
                updatedMillis = updatedMillis,
                expanded = expanded,
                menuExpanded = menuExpanded,
                onToggleExpand = onToggleExpand,
                onRefresh = onRefresh,
                onTestAll = onTestAll,
                onMenuExpandedChange = onMenuExpandedChange,
                onUpdate = onUpdate,
                onDelete = onDelete,
            )
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            InfoStrip(
                trafficUsedBytes = trafficUsedBytes,
                trafficTotalBytes = trafficTotalBytes,
            )
        }
    }
}

@Composable
private fun GroupHeader(
    title: String,
    updatedMillis: Long?,
    expanded: Boolean,
    menuExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onTestAll: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    val chevronDescription = stringResource(
        if (expanded) R.string.shroxy_group_collapse else R.string.shroxy_group_expand,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = expanded,
                onValueChange = { onToggleExpand() },
                role = Role.Button,
            )
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = chevronDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (updatedMillis != null) {
                Text(
                    text = formatUpdated(updatedMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.shroxy_group_refresh),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onTestAll) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = stringResource(R.string.shroxy_group_test_all),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box {
            IconButton(onClick = { onMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.shroxy_group_more),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shroxy_group_update)) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onUpdate()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shroxy_group_delete)) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun InfoStrip(
    trafficUsedBytes: Long?,
    trafficTotalBytes: Long?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Layer 2 strip; corner rounding comes from the card's Surface shape (square
            // bottom when expanded, 16dp when collapsed).
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = trafficText(trafficUsedBytes, trafficTotalBytes).orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun trafficText(usedBytes: Long?, totalBytes: Long?): String? {
    val units = stringArrayResource(R.array.shroxy_traffic_units).toList()
    val unlimited = stringResource(R.string.shroxy_traffic_unlimited)
    val locale = LocalConfiguration.current.locales.get(0)
    return remember(usedBytes, totalBytes, units, unlimited, locale) {
        ShroxyTrafficFormat.formatTraffic(usedBytes, totalBytes, units, unlimited, locale)
    }
}

private val updatedFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

private fun formatUpdated(updatedMillis: Long): String = updatedFormat.format(Date(updatedMillis))

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxySubscriptionGroupCollapsedPreview() {
    AppTheme {
        ShroxySubscriptionGroup(
            title = "Shroom VPN",
            updatedMillis = 1_759_576_200_000L,
            trafficUsedBytes = 3_800_000_000_000L,
            trafficTotalBytes = 0L,
            expanded = false,
            hasRows = false,
            menuExpanded = false,
            onToggleExpand = {},
            onRefresh = {},
            onTestAll = {},
            onMenuExpandedChange = {},
            onUpdate = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxySubscriptionGroupExpandedPreview() {
    AppTheme {
        ShroxySubscriptionGroup(
            title = "Shroom VPN",
            updatedMillis = 1_759_576_200_000L,
            trafficUsedBytes = 3_800_000_000_000L,
            trafficTotalBytes = 10_000_000_000_000L,
            expanded = true,
            hasRows = true,
            menuExpanded = false,
            onToggleExpand = {},
            onRefresh = {},
            onTestAll = {},
            onMenuExpandedChange = {},
            onUpdate = {},
            onDelete = {},
        )
    }
}
