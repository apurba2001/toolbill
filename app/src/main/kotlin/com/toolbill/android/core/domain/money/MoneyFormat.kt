package com.toolbill.android.core.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/**
 * The three hero sizes. The step is chosen from significant digits rather than measured width,
 * so it is stable across fonts and locales.
 *
 * 45sp fits about eleven characters at 360dp; high-denomination currencies routinely exceed
 * that, so the hero steps down instead of truncating.
 */
enum class HeroStep(val sp: Int, val lineHeightSp: Int) {
    /** ≤ 7 digits — displayMedium. */
    LARGE(45, 52),

    /** 8–9 digits — displaySmall. */
    MEDIUM(36, 44),

    /** 10+ digits — headlineLarge. Minor units carry no meaning at this scale. */
    SMALL(32, 40),
    ;

    /** Above nine digits the minor unit is dropped entirely. */
    val dropsMinorUnits: Boolean get() = this == SMALL
}

/**
 * How a currency's digits are grouped.
 *
 * Deliberately keyed off the currency rather than the device locale. The JVM's `en-IN` data
 * does not agree with Android's ICU on Indian grouping, and the design requires `₹5,30,046`
 * on every device regardless of what locale the phone is set to.
 */
enum class DigitGrouping(val groupSeparator: Char, val decimalSeparator: Char) {
    /** Groups of three: `132,480,000`. */
    WESTERN(',', '.'),

    /** Last three, then twos: `5,30,046`. */
    INDIAN(',', '.'),

    /** Groups of three with periods: `1.284.600.000`. */
    PERIOD('.', ','),
    ;

    fun group(digits: String): String {
        if (digits.length <= 3) return digits
        return if (this == INDIAN) groupIndian(digits) else groupInThrees(digits)
    }

    private fun groupInThrees(digits: String): String =
        digits.reversed().chunked(3).joinToString(groupSeparator.toString()).reversed()

    private fun groupIndian(digits: String): String {
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(groupSeparator.toString()).reversed()
        return "$grouped$groupSeparator$lastThree"
    }
}

/**
 * A money figure split into the parts the UI renders differently.
 *
 * The minor unit is rendered one step down and muted, so the eye lands on the rupees rather
 * than the paise. Keeping the split here means every surface does it the same way.
 */
data class FormattedMoney(
    val code: String,
    val symbol: String,
    val integer: String,
    val fraction: String?,
    val decimalSeparator: String,
    val integerDigits: Int,
    /**
     * Digits in the figure as written, minor units included — `44,170.50` is seven.
     *
     * Fixed at format time so that dropping the minor unit for the 10+ step does not change
     * the step that caused it to be dropped.
     */
    val significantDigits: Int,
) {
    val heroStep: HeroStep
        get() = when {
            significantDigits <= 7 -> HeroStep.LARGE
            significantDigits <= 9 -> HeroStep.MEDIUM
            else -> HeroStep.SMALL
        }

    /** The full figure without any currency marker — for aligned columns. */
    val plain: String get() = if (fraction == null) integer else "$integer$decimalSeparator$fraction"

    /**
     * ISO-code form, for the hero figure and the detail header only: `INR 44,170.50`.
     * Never combined with [withSymbol].
     */
    fun withCode(): String = "$code $plain"

    /**
     * Symbol form, for rows, chips, badges, footers, captions and widgets: `₹1,742.00`.
     * Never combined with [withCode].
     */
    fun withSymbol(): String = "$symbol$plain"

    /** Drops the minor unit — used by the widget and by the 10+ digit hero step. */
    fun withoutFraction(): FormattedMoney = copy(fraction = null)
}

/**
 * Formats minor-unit amounts for display.
 *
 * Everything is derived from the currency code, so the same amount renders identically on
 * every device. That is what makes the money rendering testable at all.
 */
object MoneyFormat {

    private val groupingByCurrency: Map<String, DigitGrouping> = mapOf(
        "INR" to DigitGrouping.INDIAN,
        "VND" to DigitGrouping.PERIOD,
        "IDR" to DigitGrouping.PERIOD,
        "EUR" to DigitGrouping.WESTERN,
    )

    private val symbolOverrides: Map<String, String> = mapOf(
        "INR" to "₹",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "VND" to "₫",
        "IDR" to "Rp",
        "CAD" to "CA$",
        "AUD" to "A$",
    )

    fun grouping(currency: String): DigitGrouping =
        groupingByCurrency[currency.uppercase(Locale.ROOT)] ?: DigitGrouping.WESTERN

    /** ISO 4217 minor-unit digits: 2 for INR and USD, 0 for VND. */
    fun fractionDigits(currency: String): Int =
        runCatching { Currency.getInstance(currency.uppercase(Locale.ROOT)).defaultFractionDigits }
            .getOrNull()
            ?.coerceAtLeast(0)
            ?: 2

    fun format(amountMinor: Long, currency: String): FormattedMoney {
        val code = currency.uppercase(Locale.ROOT)
        val digits = fractionDigits(code)
        val grouping = grouping(code)

        val major = BigDecimal(amountMinor).movePointLeft(digits).setScale(digits, RoundingMode.HALF_UP)
        val integerDigitsText = major.abs().setScale(0, RoundingMode.DOWN).toBigInteger().toString()
        val sign = if (amountMinor < 0) "−" else ""

        val fractionText = if (digits == 0) {
            null
        } else {
            major.abs()
                .remainder(BigDecimal.ONE)
                .movePointRight(digits)
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger()
                .toString()
                .padStart(digits, '0')
        }

        return FormattedMoney(
            code = code,
            symbol = symbolOverrides[code] ?: code,
            integer = sign + grouping.group(integerDigitsText),
            fraction = fractionText,
            decimalSeparator = grouping.decimalSeparator.toString(),
            integerDigits = integerDigitsText.length,
            significantDigits = integerDigitsText.length + digits,
        )
    }

    /** Convenience: symbol form, for rows and captions. */
    fun symbol(amountMinor: Long, currency: String): String =
        format(amountMinor, currency).withSymbol()

    /** Convenience: ISO-code form, for the hero and detail header. */
    fun code(amountMinor: Long, currency: String): String =
        format(amountMinor, currency).withCode()

    /** Symbol form with the minor unit dropped — the widget and most captions. */
    fun symbolWhole(amountMinor: Long, currency: String): String =
        format(amountMinor, currency).withoutFraction().withSymbol()

    /**
     * Signed delta, symbol form: `+₹696`, `−₹1,254`.
     *
     * Uses a true minus sign rather than a hyphen so it aligns with the digits in tabular mono.
     */
    fun signedSymbol(amountMinor: Long, currency: String, withFraction: Boolean = false): String {
        val magnitude = format(kotlin.math.abs(amountMinor), currency)
            .let { if (withFraction) it else it.withoutFraction() }
        return (if (amountMinor < 0) "−" else "+") + magnitude.withSymbol()
    }

    /** The hero step for an amount, without formatting it. */
    fun heroStep(amountMinor: Long, currency: String): HeroStep =
        format(amountMinor, currency).heroStep

    /**
     * The hero rendering. Above nine digits the minor unit is dropped entirely — in VND and
     * IDR it carries no meaning, and it is what was causing the figure to truncate.
     */
    fun hero(amountMinor: Long, currency: String): FormattedMoney {
        val formatted = format(amountMinor, currency)
        return if (formatted.heroStep.dropsMinorUnits) formatted.withoutFraction() else formatted
    }
}
