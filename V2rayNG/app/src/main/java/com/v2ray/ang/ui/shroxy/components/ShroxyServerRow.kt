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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme

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
) {
    // A leading emoji flag is rendered once — as the flag icon — not duplicated in the title.
    val leading = remember(title) { CountryFlags.stripLeadingFlag(title) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // Layer 1; sits flush on the expanded group card (surfaceContainer, also Layer 1).
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer)
            .selectable(
                selected = selected,
                onClick = { onClick(serverGuid) },
                role = Role.Button,
            ),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                    .width(4.dp)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (selected) 24.dp else 12.dp,
                    top = 10.dp,
                    bottom = 10.dp,
                    end = 12.dp,
                ),
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
        )
    }
}
