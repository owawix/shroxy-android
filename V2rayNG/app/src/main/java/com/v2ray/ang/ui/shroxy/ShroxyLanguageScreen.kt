package com.v2ray.ang.ui.shroxy

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.v2ray.ang.R
import com.v2ray.ang.enums.Language
import com.v2ray.ang.ui.compose.AppTheme

/**
 * Pure locale → language-row mapping. Kept free of Compose state so the default
 * selection rule is unit-testable.
 */
object ShroxyLanguages {
    const val RUSSIAN = "ru"
    const val ENGLISH = "en"

    /**
     * Resolves the selected language row. [appLocaleTag] is the AppCompat per-app locale tag
     * (null or blank when the user never picked a language); otherwise the system locale
     * decides — Russian is selected only for a Russian system locale.
     */
    fun selectedLanguage(appLocaleTag: String?, systemLanguage: String?): String =
        Language.fromLanguageTag(appLocaleTag)
            .takeUnless { it == Language.AUTO }
            ?.code
            ?: if (systemLanguage == RUSSIAN) RUSSIAN else ENGLISH
}

/**
 * Shroxy language selector (spec: settingsScreenLanguage.png): floating back button on
 * layer0, one layer1 card with «Русский» and «English» rows. The selected row gets a
 * layer2 background plus a 4dp accent bar; selection uses the same semantics pattern as
 * the server rows. Tapping a row applies it instantly via [onSelectLanguage] and the
 * screen stays open (AppCompat recreates the activity underneath).
 */
@Composable
fun ShroxyLanguageScreen(
    onBack: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The activity is recreated when the locale changes, so a remembered snapshot of the
    // current selection is refreshed on every apply.
    val selected = remember {
        val appLocaleTag = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
        // The application context keeps the system locale; the activity context is locale-overridden.
        val systemLanguage = ConfigurationCompat.getLocales(
            context.applicationContext.resources.configuration
        ).get(0)?.language
        ShroxyLanguages.selectedLanguage(appLocaleTag, systemLanguage)
    }

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
                    ShroxyLanguageRow(
                        languageCode = ShroxyLanguages.RUSSIAN,
                        label = stringResource(R.string.language_ru),
                        flagRes = R.drawable.shroxy_flag_ru,
                        selected = selected == ShroxyLanguages.RUSSIAN,
                        onClick = onSelectLanguage,
                    )
                    ShroxyLanguageRow(
                        languageCode = ShroxyLanguages.ENGLISH,
                        label = stringResource(R.string.language_en),
                        flagRes = R.drawable.shroxy_flag_us,
                        selected = selected == ShroxyLanguages.ENGLISH,
                        onClick = onSelectLanguage,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShroxyLanguageRow(
    languageCode: String,
    label: String,
    flagRes: Int,
    selected: Boolean,
    onClick: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
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
                onClick = { onClick(languageCode) },
                role = Role.Button,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Content padding is identical in selected and unselected states; the
                // accent strip is drawn, never laid out.
                .padding(start = 12.dp, top = 14.dp, bottom = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberVectorPainter(ImageVector.vectorResource(flagRes)),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 24.dp, height = 18.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12, heightDp = 917, widthDp = 412)
@Composable
private fun ShroxyLanguageScreenPreview() {
    AppTheme {
        ShroxyLanguageScreen(onBack = {}, onSelectLanguage = {})
    }
}
