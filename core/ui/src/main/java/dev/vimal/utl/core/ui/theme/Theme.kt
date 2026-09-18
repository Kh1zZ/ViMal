package dev.vimal.utl.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ViMalDarkColorScheme = darkColorScheme(
    primary          = AccentBlue,
    onPrimary        = DarkNavy,
    primaryContainer = AccentBlueDim,
    onPrimaryContainer = Ivory,
    secondary        = IvoryMuted,
    onSecondary      = DarkNavy,
    background       = DarkNavy,
    onBackground     = Ivory,
    surface          = DarkNavySurface,
    onSurface        = Ivory,
    surfaceVariant   = DarkNavyCard,
    onSurfaceVariant = IvoryMuted,
    surfaceTint      = AccentBlue,
    outline          = IvorySubtle,
    error            = ErrorRed,
    onError          = Ivory,
)

private val ViMalLightColorScheme = lightColorScheme(
    primary          = AccentBlueDim,
    onPrimary        = Ivory,
    primaryContainer = AccentBlue,
    onPrimaryContainer = DarkNavy,
    secondary        = DarkTextMuted,
    onSecondary      = LightBackground,
    background       = LightBackground,
    onBackground     = DarkText,
    surface          = LightSurface,
    onSurface        = DarkText,
    surfaceVariant   = LightCard,
    onSurfaceVariant = DarkTextMuted,
    outline          = DarkTextSubtle,
    error            = ErrorRed,
    onError          = Ivory,
)

@Composable
fun ViMalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ViMalDarkColorScheme else ViMalLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ViMalTypography,
        content = content,
    )
}
