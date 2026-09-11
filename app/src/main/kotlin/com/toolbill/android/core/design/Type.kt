package com.toolbill.android.core.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.toolbill.android.R
import com.toolbill.android.core.domain.money.HeroStep

/**
 * IBM Plex "Text" — weight 450, the one the design reaches for constantly.
 *
 * Google Fonts serves the 400 file for a 450 request, but IBM publishes the genuine face in
 * `@ibm/plex-*` as woff2, which converts cleanly to TTF. Compose accepts any weight in 1..1000,
 * so it registers as a real family member rather than being rounded to Regular.
 */
val W450 = FontWeight(450)

val IbmPlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_text, W450),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_text, W450),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

private fun sans(
    size: Int,
    lineHeight: Int,
    tracking: Double,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

/**
 * The M3 type scale, IBM Plex Sans throughout.
 *
 * Display roles are used only for money, so the scale is trimmed at the top: `displayLarge`
 * exists for completeness but the app never uses it on a 360dp screen.
 */
val ToolbillTypography = Typography(
    displayLarge = sans(57, 64, -0.25),
    displayMedium = sans(45, 52, 0.0),
    displaySmall = sans(36, 44, 0.0),
    headlineLarge = sans(32, 40, 0.0),
    headlineMedium = sans(28, 36, 0.0),
    headlineSmall = sans(24, 32, 0.0),
    titleLarge = sans(22, 28, 0.0),
    titleMedium = sans(16, 24, 0.15),
    titleSmall = sans(14, 20, 0.1, FontWeight.Medium),
    bodyLarge = sans(16, 24, 0.5),
    bodyMedium = sans(14, 20, 0.25),
    bodySmall = sans(12, 16, 0.4),
    labelLarge = sans(14, 20, 0.1, FontWeight.Medium),
    labelMedium = sans(12, 16, 0.5, FontWeight.Medium),
    labelSmall = sans(11, 16, 0.5, FontWeight.Medium),
)

private const val TABULAR_FIGURES = "tnum"

private fun mono(
    size: Double,
    lineHeight: Double,
    tracking: Double = 0.0,
    weight: FontWeight = W450,
) = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = TABULAR_FIGURES,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * The money styles: IBM Plex Mono with tabular figures on, so decimals column-align in a
 * 35-row list and the amount column never re-flows as data changes.
 *
 * Sizes are taken from the drawn components, not from the M3 role table — the hero is 44sp
 * with -1.4 tracking, not displayMedium's 45/0.
 */
@Immutable
data class MoneyTypography(
    /** ≤7 digits. The Home hero at full size. */
    val heroLarge: TextStyle,
    /** 8–9 digits. */
    val heroMedium: TextStyle,
    /** 10+ digits, minor units dropped. */
    val heroSmall: TextStyle,
    /** The minor unit beside a hero figure — one step down and muted. */
    val heroFraction: TextStyle,
    /** The ISO code label beside a hero figure. */
    val heroCode: TextStyle,
    /** The right-aligned amount on a list row. */
    val row: TextStyle,
    /** The real-charge line under a non-monthly row. */
    val rowSecondary: TextStyle,
    /** Currency codes and small mono labels. */
    val code: TextStyle,
    /** A figure in the stat row under the hero — 17sp mono. */
    val stat: TextStyle,
    /** The detail header amount. */
    val detailHeader: TextStyle,
    /** The minor unit beside the detail header amount. */
    val detailFraction: TextStyle,
    /** A monetary value typed into a field. */
    val fieldValue: TextStyle,
) {
    /** Picks the hero style for a formatted figure's step. */
    fun hero(step: HeroStep): TextStyle = when (step) {
        HeroStep.LARGE -> heroLarge
        HeroStep.MEDIUM -> heroMedium
        HeroStep.SMALL -> heroSmall
    }

    /** The minor unit is rendered proportionally to whichever hero step is in play. */
    fun heroFraction(step: HeroStep): TextStyle = when (step) {
        HeroStep.LARGE -> heroFraction
        HeroStep.MEDIUM -> heroFraction.copy(fontSize = 22.sp)
        HeroStep.SMALL -> heroFraction.copy(fontSize = 20.sp)
    }
}

val ToolbillMoneyTypography = MoneyTypography(
    // 44sp / line-height 1 / -1.4 tracking, per the drawn Money/Hero component.
    heroLarge = mono(45.0, 45.0, tracking = -1.6),
    heroMedium = mono(36.0, 36.0, tracking = -1.1),
    heroSmall = mono(32.0, 32.0, tracking = -1.0),
    heroFraction = mono(26.0, 28.0, tracking = -0.6),
    heroCode = mono(11.0, 11.0, tracking = 0.66, weight = FontWeight.Medium),
    row = mono(15.0, 20.0),
    rowSecondary = mono(10.0, 14.0, tracking = 0.6, weight = FontWeight.Medium),
    code = mono(11.0, 16.0, tracking = 0.66, weight = FontWeight.Medium),
    stat = mono(17.0, 24.0),
    detailHeader = mono(36.0, 36.0, tracking = -1.0),
    detailFraction = mono(22.0, 24.0),
    fieldValue = mono(16.0, 24.0),
)

val LocalMoneyTypography = staticCompositionLocalOf { ToolbillMoneyTypography }

/**
 * Text styles taken directly from the drawn components, where they differ from the M3 roles.
 */
object ToolbillText {
    /** Uppercase mono section label — `MONTHLY BURN`, `HOME CURRENCY`. .1em tracking. */
    val eyebrow: TextStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = W450,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp,
    )

    /** State badge — `DUE 3d`. 10sp mono, tight. */
    val badge: TextStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )

    /** List row headline — 15/20 at weight 500, not titleMedium's 16/24. */
    val rowName: TextStyle = sans(15, 20, 0.0, FontWeight.Medium)

    /** List row support line. */
    val rowSupport: TextStyle = sans(12, 16, 0.0)

    /** The business/personal glyph, 11sp mono. */
    val rowGlyph: TextStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = W450,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )

    /** Button label — 14/20 at weight 500. */
    val button: TextStyle = sans(14, 20, 0.0, FontWeight.Medium)

    /** Chip and segmented-control label — 12/16 at weight 500. */
    val chip: TextStyle = sans(12, 16, 0.0, FontWeight.Medium)

    /** The label sitting inside a text field's border. */
    val fieldLabel: TextStyle = sans(11, 13, 0.0, FontWeight.Medium)

    /** A text field's value. */
    val fieldValue: TextStyle = sans(16, 24, 0.0)

    /** Small uppercase mono label — 10sp, .1em. Stat labels and column heads. */
    val microLabel: TextStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = W450,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.0.sp,
    )

    /** Section header title — 13/18 at weight 500. */
    val sectionTitle: TextStyle = sans(13, 18, 0.1, FontWeight.Medium)

    /** Body copy inside a notice — 12/17. */
    val noticeBody: TextStyle = sans(12, 17, 0.0)

    /** A caption under a section header — 11/15. */
    val sectionCaption: TextStyle = sans(11, 15, 0.0)

    /** Pushed-screen title — 17/24 at weight 500. */
    val screenTitle: TextStyle = sans(17, 24, 0.0, FontWeight.Medium)

    /** A settings group heading — 12/16 weight 500, amber, .06em. */
    val groupHeading: TextStyle = sans(12, 16, 0.72, FontWeight.Medium)

    /** A settings row title — 15/20 at weight 400. */
    val settingsTitle: TextStyle = sans(15, 20, 0.0)

    /** A settings row body — 11.5/16. */
    val settingsBody: TextStyle = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    )

    /** The `/ month · USD 20.00` qualifier beside the detail figure. */
    val detailQualifier: TextStyle = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )

    /** A bordered card's title on the detail screen — 12/16 weight 500. */
    val detailCardTitle: TextStyle = sans(12, 16, 0.0, FontWeight.Medium)

    /** The paywall's restore action — 13/20 weight 500, muted. */
    val restoreAction: TextStyle = sans(13, 20, 0.0, FontWeight.Medium)

    /** Insights category row — 12.5/17. */
    val insightsRow: TextStyle = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    )

    /** Calendar month title — 20/28 at weight 500. */
    val monthTitle: TextStyle = sans(20, 28, 0.0, FontWeight.Medium)

    /** Calendar weekday header — 10sp mono. */
    val weekday: TextStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
    )

    /** Calendar day number — 12sp mono. */
    val dayNumber: TextStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = W450,
        fontSize = 12.sp,
        lineHeight = 12.sp,
    )

    /** The `today` / `trial` tag inside a day cell — 9sp mono. */
    val dayTag: TextStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = W450,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    )

    /** The compact app-bar title. The design's header is a 13sp label, not an M3 title. */
    val appBarTitle: TextStyle = sans(13, 18, 0.52, FontWeight.Medium)

    /** The 32dp monogram's initials. */
    val monogram: TextStyle = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 12.sp,
    )
}

/** Kept for call sites that predate [ToolbillText]. */
val EyebrowStyle: TextStyle = ToolbillText.eyebrow
