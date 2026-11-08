package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenPrimaryDark,
    onPrimary = ForestGreenOnPrimaryDark,
    primaryContainer = ForestGreenPrimaryContainerDark,
    onPrimaryContainer = ForestGreenOnPrimaryContainerDark,
    secondary = ForestGreenSecondaryDark,
    onSecondary = ForestGreenOnSecondaryDark,
    secondaryContainer = ForestGreenSecondaryContainerDark,
    onSecondaryContainer = ForestGreenOnSecondaryContainerDark,
    background = ForestGreenBackgroundDark,
    surface = ForestGreenSurfaceDark,
    surfaceVariant = ForestGreenSurfaceVariantDark,
    onSurface = ForestGreenOnSurfaceDark,
    outline = ForestGreenOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenPrimaryLight,
    onPrimary = ForestGreenOnPrimaryLight,
    primaryContainer = ForestGreenPrimaryContainerLight,
    onPrimaryContainer = ForestGreenOnPrimaryContainerLight,
    secondary = ForestGreenSecondaryLight,
    onSecondary = ForestGreenOnSecondaryLight,
    secondaryContainer = ForestGreenSecondaryContainerLight,
    onSecondaryContainer = ForestGreenOnSecondaryContainerLight,
    background = ForestGreenBackgroundLight,
    surface = ForestGreenSurfaceLight,
    surfaceVariant = ForestGreenSurfaceVariantLight,
    onSurface = ForestGreenOnSurfaceLight,
    outline = ForestGreenOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep dark green branding intentional
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
