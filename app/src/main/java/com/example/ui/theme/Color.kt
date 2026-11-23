package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// DARK MODE — "Midnight Dock"
val MidnightBackground = Color(0xFF0D1017)
val MidnightCard = Color(0xFF161A24)
val MidnightCardBorder = Color(0xFF262B38)
val MidnightPrimaryText = Color(0xFFF2F3F7)
val MidnightSecondaryText = Color(0xFF9AA0B2)
val MidnightAccent = Color(0xFF8FA8FF)
val MidnightOnAccent = Color(0xFF0D1017)
val MidnightSuccess = Color(0xFF6FD8A8)
val MidnightError = Color(0xFFFF7A8A)
val MidnightErrorContainer = Color(0xFF331820)
val MidnightSubtleSurface = Color(0xFF1E2331)

// LIGHT MODE — "Clean Shelf"
val ShelfBackground = Color(0xFFF6F7F9)
val ShelfCard = Color(0xFFFFFFFF)
val ShelfDivider = Color(0xFFEEF0F3)
val ShelfPrimaryText = Color(0xFF15171C)
val ShelfSecondaryText = Color(0xFF5A606E)
val ShelfAccent = Color(0xFF2557E0)
val ShelfOnAccent = Color(0xFFFFFFFF)
val ShelfBannerBg = Color(0xFFE4ECFF)
val ShelfBannerText = Color(0xFF1A2B5C)
val ShelfSegmentedTrack = Color(0xFFE9EBEF)
val ShelfNeutralButtonBg = Color(0xFFEEF0F4)
val ShelfSuccess = Color(0xFF188659)
val ShelfError = Color(0xFFD1324B)
val ShelfErrorContainer = Color(0xFFFDECEF)

@Immutable
data class PStoreExtendedColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val divider: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val error: Color,
    val errorContainer: Color,
    val bannerBackground: Color,
    val bannerText: Color,
    val segmentedTrack: Color,
    val neutralPillBg: Color,
    val darkGlyphOnBrand: Color,
    val lightGlyphOnBrand: Color
)

val MidnightDockColors = PStoreExtendedColors(
    isDark = true,
    background = MidnightBackground,
    card = MidnightCard,
    cardBorder = MidnightCardBorder,
    divider = MidnightCardBorder,
    primaryText = MidnightPrimaryText,
    secondaryText = MidnightSecondaryText,
    accent = MidnightAccent,
    onAccent = MidnightOnAccent,
    success = MidnightSuccess,
    error = MidnightError,
    errorContainer = MidnightErrorContainer,
    bannerBackground = MidnightSubtleSurface,
    bannerText = MidnightPrimaryText,
    segmentedTrack = MidnightSubtleSurface,
    neutralPillBg = MidnightSubtleSurface,
    darkGlyphOnBrand = MidnightBackground,
    lightGlyphOnBrand = Color.White
)

val CleanShelfColors = PStoreExtendedColors(
    isDark = false,
    background = ShelfBackground,
    card = ShelfCard,
    cardBorder = ShelfDivider,
    divider = ShelfDivider,
    primaryText = ShelfPrimaryText,
    secondaryText = ShelfSecondaryText,
    accent = ShelfAccent,
    onAccent = ShelfOnAccent,
    success = ShelfSuccess,
    error = ShelfError,
    errorContainer = ShelfErrorContainer,
    bannerBackground = ShelfBannerBg,
    bannerText = ShelfBannerText,
    segmentedTrack = ShelfSegmentedTrack,
    neutralPillBg = ShelfNeutralButtonBg,
    darkGlyphOnBrand = MidnightBackground,
    lightGlyphOnBrand = Color.White
)
