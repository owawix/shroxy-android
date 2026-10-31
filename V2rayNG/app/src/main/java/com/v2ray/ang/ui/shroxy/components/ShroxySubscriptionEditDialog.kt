package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.main.SubscriptionEditUiState

/**
 * Shroxy "Edit subscription" dialog: subscription URL, per-subscription User-Agent
 * (blank = the default Shroxy/<version> UA) and custom request headers as a JSON
 * object. Durable state (open/closed, validation error) is keyed by subscription GUID
 * in [com.v2ray.ang.ui.main.MainUiState]; the uncommitted field text is transient
 * presentation state owned by this dialog and re-seeded from [state] per GUID. Saving
 * dispatches [com.v2ray.ang.ui.main.MainAction.UpdateSubscriptionSettings], which
 * validates the JSON again before persisting anything.
 */
@Composable
fun ShroxySubscriptionEditDialog(
    state: SubscriptionEditUiState,
    onConfirm: (url: String, userAgent: String, headersJson: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable(state.subscriptionGuid) { mutableStateOf(state.subscriptionUrl) }
    var userAgent by rememberSaveable(state.subscriptionGuid) { mutableStateOf(state.userAgent) }
    var headersJson by
        rememberSaveable(state.subscriptionGuid) { mutableStateOf(state.requestHeadersJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.shroxy_sub_edit_title)) },
        text = {
            // Scrollable so every control stays reachable while the IME is open; the
            // dialog consumes its own window insets, so only the IME inset is applied here.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FormTextField(
                    label = stringResource(R.string.shroxy_sub_edit_url_label),
                    value = url,
                    onValueChange = { url = it },
                    maxLines = 2,
                )
                FormTextField(
                    label = stringResource(R.string.shroxy_sub_edit_user_agent_label),
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    supportingText = stringResource(R.string.shroxy_sub_edit_user_agent_hint),
                )
                FormTextField(
                    label = stringResource(R.string.shroxy_sub_edit_headers_label),
                    value = headersJson,
                    onValueChange = { headersJson = it },
                    placeholder = stringResource(R.string.shroxy_sub_edit_headers_placeholder),
                    isError = state.headersError != null,
                    supportingText = state.headersError,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url, userAgent, headersJson) },
                // 48dp minimum touch target for the dialog actions.
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.shroxy_sub_edit_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
