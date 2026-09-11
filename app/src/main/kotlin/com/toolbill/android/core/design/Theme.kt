package com.toolbill.android.core.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext

/**
 * The width the design was drawn against. Every dp in this app is a dp on a 360dp-wide frame.
 */
const val DESIGN_BASELINE_WIDTH_DP = 360f

/** Below this the device is a phone and gets fluid scaling; at or above it, a tablet layout. */
const val TABLET_BREAKPOINT_DP = 600

/**
 * Renders [content] as though the screen were [DESIGN_BASELINE_WIDTH_DP] wide.
 *
 * The design is specified at a 360dp baseline, so on a 412dp phone every value is correct in dp
 * yet occupies proportionally less of the screen — which reads as "everything looks small".
 * Scaling the density closes that gap: the app keeps the design's proportions on any phone.
 *
 * Only `density` is scaled — `fontScale` is passed through untouched, so a user who has raised
 * their system font size still gets it, multiplied on top.
 *
 * Scaling is clamped and stops entirely at [TABLET_BREAKPOINT_DP]: past that the answer is a
 * genuinely different layout, not a magnified phone one.
 */
@Composable
fun DesignScaled(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthDp = configuration.screenWidthDp

    val factor = when {
        widthDp >= TABLET_BREAKPOINT_DP -> 1f
        else -> (widthDp / DESIGN_BASELINE_WIDTH_DP).coerceIn(0.95f, 1.30f)
    }

    if (factor == 1f) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * factor, density.fontScale),
            content = content,
        )
    }
}

/** Which theme the user has chosen in Settings. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The Toolbill theme.
 *
 * Dynamic colour is deliberately partial. Only `primary`, `primaryContainer` and the due-soon
 * accent are taken from the wallpaper palette; surfaces stay near-neutral so a dense 35-row
 * list never tints. Overdue red and trial teal are never dynamic — they have to mean the same
 * thing on every device, and a green "overdue" badge on a green wallpaper would be a bug the
 * user could not report.
 */
@Composable
fun ToolbillTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseScheme = if (dark) ToolbillDarkColorScheme else ToolbillLightColorScheme
    val baseStateColors = if (dark) ToolbillDarkStateColors else ToolbillLightStateColors

    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val colorScheme: ColorScheme
    val stateColors: ToolbillStateColors
    if (supportsDynamic) {
        val wallpaper = if (dark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        colorScheme = baseScheme.copy(
            primary = wallpaper.primary,
            onPrimary = wallpaper.onPrimary,
            primaryContainer = wallpaper.primaryContainer,
            onPrimaryContainer = wallpaper.onPrimaryContainer,
            inversePrimary = wallpaper.inversePrimary,
            surfaceTint = wallpaper.primary,
        )
        stateColors = baseStateColors.copy(
            dueSoon = StateColor(
                content = wallpaper.primary,
                container = wallpaper.primaryContainer,
            ),
        )
    } else {
        colorScheme = baseScheme
        stateColors = baseStateColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalToolbillStateColors provides stateColors,
        LocalMoneyTypography provides ToolbillMoneyTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ToolbillTypography,
            shapes = ToolbillShapes,
        ) {
            // Paint our own ground. Without this the Activity's windowBackground shows through
            // anywhere a screen is not inside a Scaffold, which puts light-scheme text on a
            // dark window the moment the two disagree.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                DesignScaled(content)
            }
        }
    }
}

/** Shorthand accessors so call sites read as `Toolbill.stateColors.overdue`. */
object Toolbill {
    val stateColors: ToolbillStateColors
        @Composable get() = LocalToolbillStateColors.current

    val money: MoneyTypography
        @Composable get() = LocalMoneyTypography.current
}
