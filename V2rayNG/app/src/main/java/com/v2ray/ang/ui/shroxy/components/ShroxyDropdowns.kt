package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme

/**
 * Add-server menu anchored to the top-bar "+" button. Stateless: visibility is hoisted.
 */
@Composable
fun ShroxyAddServerDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddFromClipboard: () -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
            .defaultMinSize(minWidth = 240.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.shroxy_add_from_clipboard)) },
            onClick = {
                onDismiss()
                onAddFromClipboard()
            },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.shroxy_scan_qr)) },
            onClick = {
                onDismiss()
                onScanQr()
            },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxyAddServerDropdownPreview() {
    AppTheme {
        ShroxyAddServerDropdown(
            expanded = true,
            onDismiss = {},
            onAddFromClipboard = {},
            onScanQr = {},
        )
    }
}
