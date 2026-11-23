package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val MidnightMaterialScheme = darkColorScheme(
    primary = MidnightAccent,
    onPrimary = MidnightOnAccent,
    primaryContainer = MidnightSubtleSurface,
    onPrimaryContainer = MidnightAccent,
    secondary = MidnightSuccess,
    onSecondary = MidnightBackground,
    background = MidnightBackground,
    onBackground = MidnightPrimaryText,
    surface = MidnightCard,
    onSurface = MidnightPrimaryText,
    surfaceVariant = MidnightSubtleSurface,
    onSurfaceVariant = MidnightSecondaryText,
    outline = MidnightCardBorder,
    outlineVariant = MidnightCardBorder,
    error = MidnightError,
    onError = MidnightBackground,
    errorContainer = MidnightErrorContainer,
    onErrorContainer = MidnightError
)

private val CleanShelfMaterialScheme = lightColorScheme(
    primary = ShelfAccent,
    onPrimary = ShelfOnAccent,
    primaryContainer = ShelfBannerBg,
    onPrimaryContainer = ShelfBannerText,
    secondary = ShelfSuccess,
    onSecondary = ShelfOnAccent,
    background = ShelfBackground,
    onBackground = ShelfPrimaryText,
    surface = ShelfCard,
    onSurface = ShelfPrimaryText,
    surfaceVariant = ShelfSegmentedTrack,
    onSurfaceVariant = ShelfSecondaryText,
    outline = ShelfDivider,
    outlineVariant = ShelfDivider,
    error = ShelfError,
    onError = ShelfOnAccent,
    errorContainer = ShelfErrorContainer,
    onErrorContainer = ShelfError
)

val LocalPStoreColors = staticCompositionLocalOf { MidnightDockColors }

object PStoreTheme {
    val colors: PStoreExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPStoreColors.current
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val extendedColors = if (darkTheme) MidnightDockColors else CleanShelfColors
    val materialScheme = if (darkTheme) MidnightMaterialScheme else CleanShelfMaterialScheme

    CompositionLocalProvider(LocalPStoreColors provides extendedColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = PStoreTypography,
            content = content
        )
    }
}
