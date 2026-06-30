package com.momin.pipviewer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// iOS-inspired system palette ----------------------------------------------------------------

private val SystemBlue = Color(0xFF007AFF)
private val SystemBlueDark = Color(0xFF0A84FF)

/** Warm star/favorite accent (iOS yellow). Exposed for non-Material accents. */
val StarYellow = Color(0xFFFFC93C)

val LightColors = lightColorScheme(
    primary = SystemBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF00305C),
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF8A8A8E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF7F7FA),
    surfaceContainerHighest = Color(0xFFEFEFF4),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

val DarkColors = darkColorScheme(
    primary = SystemBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003063),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFF7D7AFF),
    onSecondary = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFF453A),
    onError = Color.White,
)
