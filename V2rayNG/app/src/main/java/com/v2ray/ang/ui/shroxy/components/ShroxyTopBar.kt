package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme

/**
 * Floating top bar from the Shroxy main screen spec: two ~44dp circular icon
 * buttons on layer1 — Settings on the start side, Add on the end side.
 * Each button keeps a 44dp visual circle inside a 48dp touch target (T4.1).
 * The Add button only reports [onAddClick]; the add-server dropdown menu
 * (state 8) is anchored by the caller.
 */
@Composable
fun ShroxyTopBar(
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ShroxyRoundIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.action_settings),
            onClick = onSettingsClick,
        )
        ShroxyRoundIconButton(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.action_add),
            onClick = onAddClick,
        )
    }
}

@Composable
private fun ShroxyRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 48dp hit area (T4.1 minimum) with the graphic kept in a 44dp circle.
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyTopBarPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShroxyTopBar(onSettingsClick = {}, onAddClick = {})
        }
    }
}
