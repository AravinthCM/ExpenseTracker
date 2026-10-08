package com.example.expensetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val border: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val positive: Color,
    val positiveSoft: Color,
    val negative: Color,
    val negativeSoft: Color,
    val transfer: Color,
    val transferSoft: Color,
    val isDark: Boolean
) {
    val cardBackground: Color get() = surface
    val cardBorder: Color get() = border
    val textPrimary: Color get() = ink
    val textMuted: Color get() = inkMuted
    val textFaint: Color get() = inkFaint
    val incomeGreen: Color get() = positive
    val expenseRed: Color get() = negative
    val accentBlue: Color get() = accent
    val transferPurple: Color get() = transfer
}

val LightAppColors = AppColors(
    bg = LightBg,
    surface = LightSurface,
    surfaceAlt = LightSurfaceAlt,
    ink = LightInk,
    inkMuted = LightInkMuted,
    inkFaint = LightInkFaint,
    border = LightBorder,
    accent = LightAccent,
    accentInk = LightAccentInk,
    accentSoft = LightAccentSoft,
    positive = LightPositive,
    positiveSoft = LightPositiveSoft,
    negative = LightNegative,
    negativeSoft = LightNegativeSoft,
    transfer = LightTransfer,
    transferSoft = LightTransferSoft,
    isDark = false
)

val DarkAppColors = AppColors(
    bg = DarkBg,
    surface = DarkSurface,
    surfaceAlt = DarkSurfaceAlt,
    ink = DarkInk,
    inkMuted = DarkInkMuted,
    inkFaint = DarkInkFaint,
    border = DarkBorder,
    accent = DarkAccent,
    accentInk = DarkAccentInk,
    accentSoft = DarkAccentSoft,
    positive = DarkPositive,
    positiveSoft = DarkPositiveSoft,
    negative = DarkNegative,
    negativeSoft = DarkNegativeSoft,
    transfer = DarkTransfer,
    transferSoft = DarkTransferSoft,
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightAccentInk,
    primaryContainer = LightAccentSoft,
    onPrimaryContainer = LightAccent,
    secondary = LightInkMuted,
    onSecondary = Color.White,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightInkMuted,
    outline = LightBorder,
    error = LightNegative,
    errorContainer = LightNegativeSoft,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkAccentInk,
    primaryContainer = DarkAccentSoft,
    onPrimaryContainer = DarkAccent,
    secondary = DarkInkMuted,
    onSecondary = Color.White,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkInkMuted,
    outline = DarkBorder,
    error = DarkNegative,
    errorContainer = DarkNegativeSoft,
    onError = Color.White
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            content = content
        )
    }
}
