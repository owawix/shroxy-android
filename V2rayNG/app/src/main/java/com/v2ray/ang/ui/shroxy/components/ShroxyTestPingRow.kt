package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.shroxy.formatPingMillis

/** UI state of the test/ping row: idle label, running label, or a finished result. */
sealed interface ShroxyPingUiState {
    data object Idle : ShroxyPingUiState
    data object Running : ShroxyPingUiState
    data class Result(val millis: Long) : ShroxyPingUiState
}

/**
 * Test/ping row from the Shroxy main screen spec: a ~28dp bolt badge on layer1
 * with a state label on the left, and a "Collapse all" text button on the right
 * (visible only when [hasSubscriptions]).
 */
@Composable
fun ShroxyTestPingRow(
    state: ShroxyPingUiState,
    hasSubscriptions: Boolean,
    onTestClick: () -> Unit,
    onCollapseAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClick = onTestClick)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (state) {
                    ShroxyPingUiState.Idle -> stringResource(R.string.test_connection)
                    ShroxyPingUiState.Running -> stringResource(R.string.checking)
                    is ShroxyPingUiState.Result ->
                        stringResource(R.string.ping_ms, formatPingMillis(state.millis))
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (hasSubscriptions) {
            // Text draws its glyphs at the top of any extra height, so center it
            // in the 48dp touch target to align with the leading row's content.
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onCollapseAllClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.collapse_all),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyTestPingRowIdlePreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShroxyTestPingRow(
                state = ShroxyPingUiState.Idle,
                hasSubscriptions = false,
                onTestClick = {},
                onCollapseAllClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyTestPingRowRunningPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShroxyTestPingRow(
                state = ShroxyPingUiState.Running,
                hasSubscriptions = true,
                onTestClick = {},
                onCollapseAllClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyTestPingRowResultPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShroxyTestPingRow(
                state = ShroxyPingUiState.Result(millis = 122),
                hasSubscriptions = true,
                onTestClick = {},
                onCollapseAllClick = {},
            )
        }
    }
}
