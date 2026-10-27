package com.v2ray.ang.ui.compose

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Shroxy palette (dark-only)
private val Layer0 = Color(0xFF0D0F12) // Background
private val Layer1 = Color(0xFF1A1D21) // Surface
private val Layer2 = Color(0xFF262A2F) // Surface variant
private val Accent = Color(0xFF1EE2B6) // Primary accent
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFC9CED6)

private val DarkColor = darkColorScheme(
    primary = Accent, // Accent
    onPrimary = Layer0, // Dark background for text/icons on accent
    primaryContainer = Accent, // Accent
    onPrimaryContainer = Layer0, // Dark background
    secondary = TextSecondary, // Text Secondary
    onSecondary = Layer0, // Dark background
    secondaryContainer = Layer2, // Layer 2
    onSecondaryContainer = TextPrimary, // Text Primary
    tertiary = Accent, // Accent
    onTertiary = Layer0, // Dark background
    tertiaryContainer = Layer1, // Layer 1
    onTertiaryContainer = TextPrimary, // Text Primary
    error = Color(0xFFFFB4AB), // Light Red
    errorContainer = Color(0xFF93000A), // Dark Red
    onError = Color(0xFF690005), // Deep Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Layer0, // Layer 0
    onBackground = TextPrimary, // Text Primary
    surface = Layer0, // Layer 0
    onSurface = TextPrimary, // Text Primary
    surfaceVariant = Layer2, // Layer 2
    onSurfaceVariant = TextSecondary, // Text Secondary
    outline = TextSecondary, // Text Secondary
    outlineVariant = Layer2, // Layer 2
    inverseSurface = TextPrimary, // Text Primary
    inverseOnSurface = Layer0, // Dark background
    inversePrimary = Layer0, // Dark background
    scrim = Color(0xFF000000), // Black
    surfaceTint = Accent, // Accent
    surfaceContainerLowest = Layer0, // Layer 0
    surfaceContainerLow = Layer0, // Layer 0
    surfaceContainer = Layer1, // Layer 1
    surfaceContainerHigh = Layer1, // Layer 1
    surfaceContainerHighest = Layer2, // Layer 2
)

// Shroxy power-button tokens that have no Material color role in the scheme above.
val ColorScheme.powerRingActiveStart: Color
    get() = Color(0xFFA9FFE5) // Mint: active gradient start (top-left) and inner circle fill

val ColorScheme.powerRingInactiveEnd: Color
    get() = Color(0xFF8B9099) // Gray: inactive gradient end (bottom-right) and inactive icon tint

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    // Shroxy is dark-only: always use the dark color scheme regardless of system or stored mode.
    return true
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    // Shroxy uses a fixed palette; dynamic color is disabled.
    val colorScheme = DarkColor
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
