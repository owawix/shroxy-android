package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.shroxy.RowPingUiState
import com.v2ray.ang.ui.shroxy.rowPingUiState

/**
 * A single server row in an expanded subscription group. Stateless: selection and identity
 * are hoisted; [serverGuid] is the stable domain ID used for click dispatch and list keys.
 */
@Composable
fun ShroxyServerRow(
    serverGuid: String,
    title: String,
    subtitle: String,
    remark: String,
    country: String?,
    selected: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomCornerRadius: Dp = 0.dp,
    /** Last bulk-test delay for this server in ms; null (or absent) hides the trailing value. */
    delayMillis: Long? = null,
    /** True while this row's subscription group bulk test is running (pending rows spin). */
    isGroupTesting: Boolean = false,
) {
    // A leading emoji flag is rendered once — as the flag icon — not duplicated in the title.
    val leading = remember(title) { CountryFlags.stripLeadingFlag(title) }
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // Layer 1 (Layer 2 when selected); sits flush on the expanded group card
            // (surfaceContainer, also Layer 1). Square top corners — the rows visually
            // continue the card; the last row rounds only its bottom corners.
            .background(
                color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = bottomCornerRadius,
                    bottomEnd = bottomCornerRadius,
                ),
            )
            // Selected rows get a 4dp "border-left" accent drawn behind the content but
            // above the layer2 background. drawBehind paints only — it never participates
            // in measurement, so the strip cannot stretch the row or shift the padding.
            .then(
                if (selected) {
                    Modifier.drawBehind {
                        val w = 4.dp.toPx()
                        val h = size.height - 8.dp.toPx()
                        if (h > 0f) {
                            drawRoundRect(
                                color = accent,
                                topLeft = Offset(0f, (size.height - h) / 2f),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                    }
                } else {
                    Modifier
                }
            )
            .selectable(
                selected = selected,
                onClick = { onClick(serverGuid) },
                role = Role.Button,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Content padding is identical in selected and unselected states; the
                // accent strip is drawn, never laid out.
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountryFlag(
                remark = leading.emoji ?: remark,
                country = country,
                modifier = Modifier.size(width = 28.dp, height = 20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = leading.cleanTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Trailing per-server slot of the group speed test: a small spinner while the
            // row's group test is running and this row has no result yet, then the value
            // or N/A; outside a running test an unmeasured row keeps an empty slot.
            when (val rowState = rowPingUiState(isGroupTesting = isGroupTesting, delayMillis = delayMillis)) {
                RowPingUiState.Loading -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    val checking = stringResource(R.string.ping_checking)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .semantics { contentDescription = checking },
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is RowPingUiState.Value -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.server_test_delay_value, rowState.millis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RowPingUiState.NotAvailable -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ping_not_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RowPingUiState.None -> Unit
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyServerRowSelectedPreview() {
    AppTheme {
        ShroxyServerRow(
            serverGuid = "guid-se-1",
            title = "Швеция",
            subtitle = "VLESS / TCP / TLS",
            remark = "Швеция",
            country = "SE",
            selected = true,
            onClick = {},
            delayMillis = 145,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyServerRowUnselectedPreview() {
    AppTheme {
        ShroxyServerRow(
            serverGuid = "guid-nl-1",
            title = "Нидерланды • Hysteria",
            subtitle = "Hysteria / HYSTERIA / TLS",
            remark = "🇳🇱 Нидерланды",
            country = null,
            selected = false,
            onClick = {},
            bottomCornerRadius = 16.dp,
        )
    }
}
