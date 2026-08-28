package com.toolbill.android.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Dark scheme — the primary design.
//
// Warm neutral, not the default purple-tinted M3 dark. Surface steps are 4–6 tones apart so
// dividers still read without elevation.
// ---------------------------------------------------------------------------------------------

private val DarkPrimary = Color(0xFFF2C35C)
private val DarkOnPrimary = Color(0xFF3A2B00)
private val DarkPrimaryContainer = Color(0xFF54400A)
private val DarkOnPrimaryContainer = Color(0xFFFFDF9E)

private val DarkSecondary = Color(0xFFC3C7C1)
private val DarkOnSecondary = Color(0xFF2C312D)
private val DarkSecondaryContainer = Color(0xFF424743)
private val DarkOnSecondaryContainer = Color(0xFFDFE4DE)

private val DarkTertiary = Color(0xFF7FD4C1)
private val DarkOnTertiary = Color(0xFF003730)
private val DarkTertiaryContainer = Color(0xFF0F3B34)
private val DarkOnTertiaryContainer = Color(0xFF9BF0DC)

private val DarkError = Color(0xFFFF6B5E)
private val DarkOnError = Color(0xFF4A0F0A)
private val DarkErrorContainer = Color(0xFF4A1611)
private val DarkOnErrorContainer = Color(0xFFFFDAD5)

private val DarkSurfaceContainerLowest = Color(0xFF0C0B0A)
private val DarkSurface = Color(0xFF121110)
private val DarkSurfaceContainerLow = Color(0xFF1A1917)
private val DarkSurfaceContainer = Color(0xFF1E1D1B)
private val DarkSurfaceContainerHigh = Color(0xFF292724)
private val DarkSurfaceContainerHighest = Color(0xFF343130)
private val DarkSurfaceVariant = Color(0xFF302E2A)
private val DarkOnSurface = Color(0xFFEAE6E0)
private val DarkOnSurfaceVariant = Color(0xFFA9A39A)
private val DarkOutline = Color(0xFF6C6862)
private val DarkOutlineVariant = Color(0xFF4A4842)
private val DarkInverseSurface = Color(0xFFEAE6E0)

/** The branded dark scheme. This is the default and the one the design was drawn against. */
val ToolbillDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = Color(0xFF7A5A00),
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = Color(0xFF1B1A17),
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = DarkSurfaceContainerHighest,
    surfaceDim = DarkSurfaceContainerLowest,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)

// ---------------------------------------------------------------------------------------------
// Light scheme — a second pass, not an inversion.
//
// Primary drops to tone 30 so amber stays legible on paper-white. Neutrals warm slightly rather
// than going pure grey; state colors are re-picked, not flipped.
// ---------------------------------------------------------------------------------------------

private val LightPrimary = Color(0xFF7A5A00)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFFFE0A0)
private val LightOnPrimaryContainer = Color(0xFF261A00)

private val LightSecondary = Color(0xFF5A5F5A)
private val LightSecondaryContainer = Color(0xFFDDE3DC)
private val LightTertiary = Color(0xFF00695C)
private val LightTertiaryContainer = Color(0xFFA8F0E0)
private val LightError = Color(0xFFB3261E)
private val LightErrorContainer = Color(0xFFFFDAD5)

private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurface = Color(0xFFFCFBF8)
private val LightSurfaceContainerLow = Color(0xFFF7F5F0)
private val LightSurfaceContainer = Color(0xFFF2F0EA)
private val LightSurfaceContainerHigh = Color(0xFFECE9E2)
private val LightSurfaceContainerHighest = Color(0xFFE6E3DB)
private val LightSurfaceVariant = Color(0xFFE7E3DA)
private val LightOnSurface = Color(0xFF1B1A17)
private val LightOnSurfaceVariant = Color(0xFF55524A)
private val LightOutline = Color(0xFF86817A)
private val LightOutlineVariant = Color(0xFFD4D0C7)

/**
 * The branded light scheme.
 *
 * The design enumerates the tones above; the `on*` pairs for secondary, tertiary and error are
 * derived here to the same contrast intent, since a ColorScheme needs all of them.
 */
val ToolbillLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = DarkPrimary,
    secondary = LightSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF171D19),
    tertiary = LightTertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = Color(0xFF00201B),
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = Color(0xFF302E2A),
    inverseOnSurface = Color(0xFFF7F5F0),
    error = LightError,
    onError = Color(0xFFFFFFFF),
    errorContainer = LightErrorContainer,
    onErrorContainer = Color(0xFF410002),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = LightSurfaceContainerLowest,
    surfaceDim = LightSurfaceContainerHighest,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)
