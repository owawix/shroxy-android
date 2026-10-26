package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.powerRingActiveStart
import com.v2ray.ang.ui.compose.powerRingInactiveEnd
import com.v2ray.ang.ui.shroxy.formatSessionDuration

/** Visual state of the Shroxy power button. */
enum class ShroxyPowerState {
    Idle,
    Connected,
    TestRunning,
}

/**
 * Large concentric power button from the Shroxy main screen spec:
 * ~176dp outer circle with a top-left → bottom-right linear gradient ring and a
 * ~128dp solid inner circle. Active = mint gradient, inactive = gray gradient.
 */
@Composable
fun ShroxyPowerButton(
    state: ShroxyPowerState,
    sessionSeconds: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val active = state == ShroxyPowerState.Connected
    val ringGradient = if (active) {
        listOf(colors.powerRingActiveStart, colors.primary)
    } else {
        listOf(colors.onSurfaceVariant, colors.powerRingInactiveEnd)
    }
    val innerColor = if (active) colors.powerRingActiveStart else colors.onSurfaceVariant
    val contentColor = if (active) colors.onPrimary else colors.powerRingInactiveEnd

    Box(
        modifier = modifier
            .size(176.dp)
            .background(Brush.linearGradient(ringGradient), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(innerColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = stringResource(R.string.action_toggle_vpn),
                    modifier = Modifier.size(36.dp),
                    tint = contentColor,
                )
                when (state) {
                    ShroxyPowerState.Connected -> {
                        Text(
                            text = stringResource(R.string.connected),
                            color = contentColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatSessionDuration(sessionSeconds),
                            color = contentColor,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFeatureSettings = "tnum",
                            ),
                        )
                    }

                    ShroxyPowerState.TestRunning -> {
                        Text(
                            text = stringResource(R.string.test),
                            color = contentColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    ShroxyPowerState.Idle -> Unit
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyPowerButtonIdlePreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShroxyPowerButton(state = ShroxyPowerState.Idle, sessionSeconds = 0, onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyPowerButtonConnectedPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShroxyPowerButton(
                state = ShroxyPowerState.Connected,
                sessionSeconds = 4509L,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyPowerButtonTestRunningPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShroxyPowerButton(
                state = ShroxyPowerState.TestRunning,
                sessionSeconds = 0,
                onClick = {},
            )
        }
    }
}
