package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme

/**
 * Building blocks of the Shroxy settings card (spec: settingsScreen.png).
 * Both rows are stateless: the language row reports [onClick], the routing row
 * reports [onCheckedChange] and leaves the checked value to the caller.
 */

/** ~40dp circular icon badge on layer2 with a white icon. */
@Composable
fun ShroxySettingsIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** «Язык» row: badge, bold title, subtitle; the whole row navigates to the language screen. */
@Composable
fun ShroxyLanguageSettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShroxySettingsIconBadge(icon = Icons.Outlined.Language)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * «Маршрутизация» row: the whole row is one Switch-role toggleable node (one focus
 * target, stateDescription for TalkBack); the trailing Switch only visualizes the
 * state, so it has no click handler of its own.
 */
@Composable
fun ShroxyRoutingSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateDescription = stringResource(
        if (checked) R.string.routing_switch_on else R.string.routing_switch_off
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { this.stateDescription = stateDescription }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShroxySettingsIconBadge(icon = Icons.Filled.AltRoute)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12, heightDp = 280, widthDp = 412)
@Composable
private fun ShroxySettingsItemsPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column {
                        ShroxyLanguageSettingRow(
                            title = stringResource(R.string.language),
                            subtitle = stringResource(R.string.language_subtitle),
                            onClick = {},
                        )
                        ShroxyRoutingSettingRow(
                            title = stringResource(R.string.routing),
                            subtitle = stringResource(R.string.routing_subtitle),
                            checked = true,
                            onCheckedChange = {},
                        )
                    }
                }
            }
        }
    }
}
