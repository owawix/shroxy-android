package com.v2ray.ang.ui.shroxy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.ui.shroxy.components.ShroxyLanguageSettingRow
import com.v2ray.ang.ui.shroxy.components.ShroxyRoutingSettingRow

/**
 * Shroxy settings screen (spec: settingsScreen.png): floating back button on layer0,
 * one layer1 card with the «Язык» row (opens the language screen) and the
 * «Маршрутизация» row with the bypass-RU switch. All operations go through
 * [MainAction]/[MainViewModel]; the language screen callback stays pluggable for T5.2.
 */
@Composable
fun ShroxySettingsScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Floating circular back button: 48dp touch target, 44dp circle on layer1.
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(48.dp)
                    .clickable(onClick = onBack),
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ShroxyLanguageSettingRow(
                        title = stringResource(R.string.language),
                        subtitle = stringResource(R.string.language_subtitle),
                        onClick = onOpenLanguage,
                    )
                    ShroxyRoutingSettingRow(
                        title = stringResource(R.string.routing),
                        subtitle = stringResource(R.string.routing_subtitle),
                        checked = uiState.bypassRu,
                        onCheckedChange = { onAction(MainAction.SetBypassRu(it)) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12, heightDp = 917, widthDp = 412)
@Composable
private fun ShroxySettingsScreenPreview() {
    AppTheme {
        // Static preview of the screen chrome; live state needs a ViewModel.
        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ShroxyLanguageSettingRow(
                                title = stringResource(R.string.language),
                                subtitle = stringResource(R.string.language_subtitle),
                                onClick = {},
                            )
                            ShroxyRoutingSettingRow(
                                title = stringResource(R.string.routing),
                                subtitle = stringResource(R.string.routing_subtitle),
                                checked = false,
                                onCheckedChange = {},
                            )
                        }
                    }
                }
            }
        }
    }
}
