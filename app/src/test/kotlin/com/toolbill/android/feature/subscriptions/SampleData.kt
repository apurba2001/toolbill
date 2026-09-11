package com.toolbill.android.feature.subscriptions

import com.toolbill.android.core.domain.subscription.PricedSubscription
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.time.LocalDate



/**
 * The exact worked example from the design document.
 *
 * Test fixture only — it lives in the test source set so it cannot ship in the APK and cannot
 * be reached from a screen by accident. Every screen now reads the repository; this is what
 * the domain calculations are checked against, figure by figure.
 */
object SampleData {

    /** The day the design document is drawn on: Vercel renews "in 1 day" on 25 August. */
    val today: LocalDate = LocalDate.of(2026, 8, 24)

    const val HOME_CURRENCY = "INR"

    private fun sub(
        id: String,
        name: String,
        amountMinor: Long,
        currency: String,
        cycle: BillingCycle,
        anchor: LocalDate,
        category: Category,
        isBusiness: Boolean = true,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        trialEnd: LocalDate? = null,
        resume: LocalDate? = null,
        cancelled: LocalDate? = null,
        otherLabel: String? = null,
    ) = Subscription(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currency = currency,
        cycle = cycle,
        anchorDate = anchor,
        category = category,
        otherLabel = otherLabel,
        isBusiness = isBusiness,
        status = status,
        trialEndDate = trialEnd,
        resumeDate = resume,
        cancelledDate = cancelled,
    )

    val aws = PricedSubscription(
        sub(
            "aws", "AWS", 8510, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2025, 9, 2), Category.HOSTING_INFRA,
        ),
        homeAmountMinor = 741200,
    )

    val heroku = PricedSubscription(
        sub(
            "heroku", "Heroku Dynos", 2500, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2025, 8, 22), Category.HOSTING_INFRA,
        ),
        homeAmountMinor = 218000,
        overdueSince = LocalDate.of(2026, 8, 22),
    )

    val vercel = PricedSubscription(
        sub(
            "vercel", "Vercel Pro", 2000, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2025, 10, 25), Category.HOSTING_INFRA,
        ),
        homeAmountMinor = 174200,
    )

    val claude = PricedSubscription(
        sub(
            "claude", "Claude Pro", 2000, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2025, 10, 27), Category.AI_TOOLS,
        ),
        homeAmountMinor = 174200,
    )

    val sentry = PricedSubscription(
        sub(
            "sentry", "Sentry Team", 2800, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2025, 11, 27), Category.ANALYTICS,
        ),
        homeAmountMinor = 243600,
    )

    val googleWorkspace = PricedSubscription(
        sub(
            "gworkspace", "Google Workspace", 102000, "INR", BillingCycle.MONTHLY,
            LocalDate.of(2025, 9, 27), Category.PRODUCTIVITY,
        ),
        homeAmountMinor = 102000,
    )

    val namecheap = PricedSubscription(
        sub(
            "namecheap", "Namecheap · 3 domains", 3651, "USD", BillingCycle.ANNUAL,
            LocalDate.of(2025, 8, 29), Category.DOMAINS,
        ),
        homeAmountMinor = 317900,
    )

    val flyIo = PricedSubscription(
        sub(
            "fly", "Fly.io", 2800, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2025, 12, 30), Category.HOSTING_INFRA,
        ),
        homeAmountMinor = 243600,
    )

    // Anchored on the 31st, so the schedule exercises the return-to-anchor rule in production.
    val resend = PricedSubscription(
        sub(
            "resend", "Resend Pro", 2000, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2025, 10, 31), Category.EMAIL_MARKETING,
        ),
        homeAmountMinor = 174200,
    )

    val spotify = PricedSubscription(
        sub(
            "spotify", "Spotify Duo", 14900, "INR", BillingCycle.MONTHLY,
            LocalDate.of(2025, 7, 31), Category.ENTERTAINMENT, isBusiness = false,
        ),
        homeAmountMinor = 14900,
    )

    val adobe = PricedSubscription(
        sub(
            "adobe", "Adobe Creative Cloud", 5499, "USD", BillingCycle.ANNUAL,
            LocalDate.of(2025, 10, 4), Category.DESIGN,
        ),
        homeAmountMinor = 466200,
    )

    val linear = PricedSubscription(
        sub(
            "linear", "Linear Standard", 800, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2026, 8, 30), Category.PRODUCTIVITY,
            status = SubscriptionStatus.TRIAL,
            trialEnd = LocalDate.of(2026, 8, 30),
        ),
        homeAmountMinor = 69600,
    )

    val shopify = PricedSubscription(
        sub(
            "shopify", "Shopify Basic", 249900, "INR", BillingCycle.MONTHLY,
            LocalDate.of(2025, 6, 1), Category.OTHER,
            status = SubscriptionStatus.PAUSED,
            resume = LocalDate.of(2026, 10, 1),
            otherLabel = "commerce",
        ),
        homeAmountMinor = 249900,
    )

    val notion = PricedSubscription(
        sub(
            "notion", "Notion Plus", 88000, "INR", BillingCycle.MONTHLY,
            LocalDate.of(2025, 7, 12), Category.PRODUCTIVITY,
            status = SubscriptionStatus.CANCELLED,
            cancelled = LocalDate.of(2026, 7, 12),
        ),
        homeAmountMinor = 88000,
    )

    val figma = PricedSubscription(
        sub(
            "figma", "Figma Professional", 1500, "USD", BillingCycle.MONTHLY,
            LocalDate.of(2025, 9, 11), Category.DESIGN,
        ),
        homeAmountMinor = 134000,
    )

    /** The eight-row list from section 03, in the design's own order. */
    val eightItemList = listOf(
        aws, heroku, vercel, linear, adobe, namecheap, shopify, notion,
    )

    /** The eight charges falling in 25–31 August, in date order. */
    val nextSevenDays: List<Pair<PricedSubscription, LocalDate>> = listOf(
        vercel to LocalDate.of(2026, 8, 25),
        claude to LocalDate.of(2026, 8, 27),
        sentry to LocalDate.of(2026, 8, 27),
        googleWorkspace to LocalDate.of(2026, 8, 27),
        namecheap to LocalDate.of(2026, 8, 29),
        flyIo to LocalDate.of(2026, 8, 30),
        resend to LocalDate.of(2026, 8, 31),
        spotify to LocalDate.of(2026, 8, 31),
    )

    /** Everything, for the All screen. */
    val all = listOf(
        aws, heroku, vercel, claude, sentry, googleWorkspace, namecheap,
        flyIo, resend, spotify, adobe, figma, linear, shopify, notion,
    )

    /** The design's stated Home hero: ₹44,170.50 a month across 31 active subscriptions. */
    const val MONTHLY_BURN_MINOR = 4_417_050L
    const val ACTIVE_COUNT = 31
    const val TRACKED_COUNT = 35
    const val BUSINESS_SHARE_PERCENT = 96

    /** The FX notice thresholds — both must be crossed before the user hears anything. */
    const val FX_DELTA_MINOR = 125_400L
    const val FX_MOVE_PERCENT = "3.1%"
    const val USD_BILLED_BURN_MINOR = 4_043_800L
}
