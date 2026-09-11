package com.toolbill.android.core.domain.subscription

import com.toolbill.android.core.domain.money.FxRates
import java.time.LocalDate

/**
 * A subscription paired with what its charge currently costs in the home currency.
 *
 * The home figure is carried alongside rather than on the entity, because it is derived from
 * today's rate and changes without the subscription changing.
 */
data class PricedSubscription(
    val subscription: Subscription,
    val homeAmountMinor: Long,
    val overdueSince: LocalDate? = null,
)

/**
 * Prices one subscription in [homeCurrency].
 *
 * The single conversion rule in the app: the widget, the view model and any future importer
 * all call this, so a row cannot be worth one figure on the home screen and another on the
 * home *screen widget*.
 *
 * A currency with no captured rate keeps its face amount rather than being dropped. That can
 * only arrive by import — the add sheet offers exactly the codes [FxRates] covers — and showing
 * an unconverted figure is recoverable where hiding the user's own entry is not.
 */
fun Subscription.pricedIn(homeCurrency: String): PricedSubscription = PricedSubscription(
    subscription = this,
    homeAmountMinor = FxRates.convertMinor(amountMinor, currency, homeCurrency) ?: amountMinor,
    // Overdue means a charge fell due and did not go through, and nothing tells Toolbill that:
    // it observes the schedule, not the card. Left null rather than guessed.
    overdueSince = null,
)
