# Toolbill — End-to-End Plan

**A local-first SaaS spend tracker for freelancers and solo founders.**
Native Android · Kotlin + Jetpack Compose · No backend server.

---

## Contents

1. [Product thesis](#1-product-thesis)
2. [Feature inventory](#2-feature-inventory)
3. [Architecture](#3-architecture)
4. [Data model](#4-data-model)
5. [The three hard parts](#5-the-three-hard-parts)
6. [Build timeline](#6-build-timeline)
7. [Play Store setup](#7-play-store-setup)
8. [Business model](#8-business-model)
9. [Launch plan](#9-launch-plan)
10. [Metrics and kill criteria](#10-metrics-and-kill-criteria)
11. [Open decisions](#11-open-decisions)
12. [Risk register](#12-risk-register)
13. [Pre-flight checklist](#13-pre-flight-checklist)

---

## 1. Product thesis

### The problem

Freelancers and solo founders pay for 25–40 software tools. They track them in a spreadsheet they never update, get surprised by renewals, and cannot answer "what does my stack actually cost me per month." If they earn in one currency and pay in USD, the real cost drifts every month and they never see it.

### Why existing apps don't solve it

| Category | Weakness |
|---|---|
| Plaid-based trackers (Rocket Money, etc.) | Require bank credentials. Hard no for a large privacy-conscious segment. |
| Manual trackers (Bobby, Subby) | Abandoned, no modern sync, consumer-priced at $2.99 so no incentive to maintain. |
| Spreadsheets | Free and flexible, but nobody keeps them current, and they don't remind you. |

### The four differentiators

Everything else is table stakes. These are the reasons someone switches:

1. **Normalized monthly cost.** Every subscription reduced to a true per-month figure, so a ₹4,662/year tool and a ₹388/month tool sort correctly against each other. Most trackers get this wrong.
2. **Real FX cost tracking.** You're charged $20 — what did that actually cost in your currency this month versus last? Per-subscription drift and aggregate impact.
3. **No bank credentials, ever.** Manual entry, data on device. The privacy story Plaid-based apps structurally cannot tell.
4. **Unlimited entries free.** No 5-subscription cap forcing a paywall before the user sees value.

### Positioning

Not a consumer finance app. The user is technical and taste-sensitive — they use Linear, Raycast, Obsidian. No gamification, no mascots, no streaks, no motivational copy. A tool, not a dashboard product.

### Why this product for this developer

- **Dogfoodable.** You pay for Shopify, Qikink, domains, hosting, Claude, analytics across BytWear and AdStockGuard. You are the user. This matters more than anything else at 10 hrs/week — you can make product decisions without guessing.
- **The hard part is your day job.** Normalization across billing cycles, FX drift, charge-vs-contract reconciliation. This is payments work.
- **B2B framing clears the price objection.** A business expense at ₹1,499/yr is trivially justified. Consumers won't pay ₹1,499 to track Netflix.

---

## 2. Feature inventory

### Free tier

**Core tracking**
- Unlimited subscriptions, no cap, permanently
- Monthly burn + annualized total
- Next 7 days of renewals
- Multi-currency entry with conversion to home currency
- Business vs personal tagging
- 11 fixed categories + "Other" with custom label
- Notes per subscription

**Billing cycles**
- Monthly, annual, quarterly, weekly
- Custom: every N days / weeks / months / years
- Anchor-date handling that respects real calendar dates

**States**
- Active, due soon, overdue, free trial, paused, cancelled
- Trial end dates with first-charge preview
- Cancelled subscriptions retained for records, excluded from totals

**Browsing**
- Dense list, 35+ rows comfortably visible
- Sort by cost / renewal date / name
- Filter by business / personal / cancelled / paused
- Search across name, category, notes
- Calendar month grid with per-day charges
- Per-subscription detail with full payment history

**Entry speed**
- Bottom sheet, keyboard up on open
- ~400 known SaaS names bundled — typing "Clau" prefills currency, cycle, category (**not** amount)
- "Save & add another" for bulk sessions
- Long-press row: mark paid, skip charge, pause, duplicate, edit, delete
- Import from CSV or JSON with column mapping

**Interface**
- Light / dark / system, Material You dynamic color
- Home screen widget, three sizes
- Fully offline

### Pro — ₹1,499 / $19 per year, or ₹3,099 / $39 lifetime

| Feature | Detail |
|---|---|
| Renewal notifications | Configurable lead time, fired locally. No server sees your renewals. |
| CSV export | One row per charge with the FX rate applied that date. Built for an accountant. |
| Insights | Category spend, business/personal split, annual-vs-monthly savings, FX drift YTD |
| Google Drive backup | Your Drive, your file. Restores on a new phone. |

**On lapse:** nothing deleted or hidden. Entries stay, the app keeps working, reminders and exports stop.

### Deliberately excluded

Each of these will be requested. Decline all of them.

- No bank connections — it's the positioning, not a limitation
- No accounts or login
- No multi-user or sharing — this is what keeps it serverless
- No ads, ever
- No email/SMS auto-detection — needs permissions that contradict the privacy story
- No budget limits, goals, or streaks — this audience finds them patronizing

---

## 3. Architecture

### Stack

| Layer | Choice | Rationale |
|---|---|---|
| UI | Compose + Material 3 | Design spec is already written against it |
| Navigation | Navigation Compose (type-safe routes) | Stable, no library churn |
| Database | Room + KSP | SQLite with migrations and test support |
| Preferences | DataStore (Proto) | Settings, home currency, thresholds |
| DI | Manual or Koin | Skip Hilt — compile-time cost not worth it solo |
| Async | Coroutines + Flow | Room emits Flow natively |
| Dates | `java.time` + core library desugaring | Never `Calendar` |
| Reminders | AlarmManager (inexact window) + WorkManager safety net | Exact alarms need a permission you can't justify |
| Widget | Glance | Spec'd for three sizes |
| Billing | RevenueCat + Play Billing 7 | Free under $2.5K MTR |
| Backup | Drive REST v3, `appDataFolder`, Credential Manager | Hidden per-app folder |
| FX | OkHttp + kotlinx-serialization → Frankfurter | Free, no key, ECB-sourced |
| Testing | JUnit5, Turbine, Room migration tests, Paparazzi | Date engine needs real coverage |

### Module structure

Single Gradle module, packages by feature. Multi-module buys build speed you don't need at this size and costs configuration time you can't spare.

```
app/
  core/
    data/          Room entities, DAOs, repositories
    domain/        Pure Kotlin — date engine, normalization, FX math
    design/        Theme, tokens, shared components
  feature/
    subscriptions/ List, add/edit, detail, search
    calendar/
    insights/
    export/
    settings/
    backup/
    paywall/
  widget/          Glance
```

`core/domain` must have **zero Android dependencies**. That is what makes the date engine testable on the JVM without an emulator.

### The "no server" boundary

| Concern | How it works without a server |
|---|---|
| Storage | Room, on device |
| Sync | Google Drive `appDataFolder` — client-side OAuth, no backend |
| Billing | RevenueCat validates Play receipts |
| Notifications | AlarmManager, scheduled locally |
| FX rates | Direct client call to Frankfurter, cached |

You never run infrastructure. Note: `appDataFolder` **does** count against the user's Drive quota — do not claim otherwise in-app.

---

## 4. Data model

```kotlin
@Entity
data class SubscriptionEntity(
    @PrimaryKey val id: String,          // UUID
    val name: String,
    val amountMinor: Long,               // paise/cents — NEVER Double
    val currency: String,                // ISO 4217
    val cycleUnit: CycleUnit,            // DAY, WEEK, MONTH, YEAR
    val cycleCount: Int,                 // "every 3 MONTH"
    val anchorDate: LocalDate,           // first charge
    val category: Category,
    val otherLabel: String?,
    val isBusiness: Boolean,
    val status: Status,                  // ACTIVE, TRIAL, PAUSED, CANCELLED
    val trialEndDate: LocalDate?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant               // drives last-write-wins sync
)

@Entity
data class ChargeEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val date: LocalDate,
    val status: ChargeStatus,            // PAID, SKIPPED
    val amountMinorOriginal: Long,
    val currencyOriginal: String,
    val amountMinorHome: Long?,          // null when rate unknown (pre-install)
    val fxRate: BigDecimal?,             // null renders as "—"
    val isUserOverridden: Boolean
)
```

### Three rules that prevent most bugs

**1. Money is `Long` minor units. Never `Double`.**
The single most common bug in this category.

**2. Store `cycleUnit` + `cycleCount`, not `cycleDays`.**
"Monthly on the 31st" is not "every 30.44 days" — it must land on real calendar dates.

**3. Normalization is derived, never stored.**

```kotlin
val normalizedMonthlyMinor: Long get() = when (cycleUnit) {
    DAY   -> amountMinorHome * 3653 / (cycleCount * 1200)   // 365.25/12
    WEEK  -> amountMinorHome * 5218 / (cycleCount * 1200)   // 52.18/12
    MONTH -> amountMinorHome / cycleCount
    YEAR  -> amountMinorHome / (cycleCount * 12)
}
```

Compute in Kotlin, not SQL. At 35 rows the DB-level sort buys nothing and a generated column complicates migrations.

**Every total in the app sorts and sums on `normalizedMonthly`, excluding TRIAL, PAUSED, and CANCELLED.** Trials are surfaced separately as "+₹696/mo from 30 Aug."

---

## 5. The three hard parts

Everything else is CRUD. Budget accordingly.

### 5.1 The date engine — one week

```kotlin
fun nextChargeDate(
    anchor: LocalDate,
    unit: CycleUnit,
    count: Int,
    after: LocalDate
): LocalDate
```

Pure functions, no Android dependencies. **Write the tests before the implementation.**

Cases that will bite:

| Case | Correct behaviour |
|---|---|
| Anchor 31 Jan, MONTH cycle | Feb → 28th, **March → 31st** (must return to anchor, not stay at 28) |
| Anchor 29 Feb, YEAR cycle | Non-leap years → 28 Feb, leap years → 29 Feb |
| Anchor 31st, quarterly | Same return-to-anchor rule |
| DST transitions | Only matters when converting to alarm times — keep dates as `LocalDate`, convert late |
| Timezone change mid-cycle | Recompute, don't shift stored dates |

The return-to-anchor rule is the classic bug. Naive implementations clamp to 28 and never recover.

### 5.2 Reminder delivery — one week

Layered, because any single mechanism fails on some device.

- **Primary:** `AlarmManager.setWindow()` with one-hour tolerance. A 3-day-ahead reminder doesn't need exactness, which keeps you clear of `USE_EXACT_ALARM` policy restrictions (Play limits it to alarm clock and calendar apps).
- **Permission:** `POST_NOTIFICATIONS` requested after the first subscription is added, never on launch, with a rationale screen first.
- **Reschedule on:** data change, `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`.
- **Safety net:** daily `PeriodicWorkRequest` that checks for missed reminders and fires them late rather than never.
- **Observability:** record `lastFiredAt` so the diagnostic screen's self-check tells the truth.
- **Diagnostic screen:** OEM-aware guidance deep-linking to battery settings. Source guidance from dontkillmyapp.com.

**Test on a real Xiaomi, Realme, or Oppo device.** Emulators do not reproduce OEM battery policy, which is exactly where this breaks. A renewal reminder that silently doesn't fire is a one-star review.

### 5.3 Drive backup and restore — one week

- Serialize the whole DB to one JSON blob in `appDataFolder`, gzip it.
- Last-write-wins on `updatedAt`. At 35 subscriptions and a few hundred charges you're under 100KB — **do not build delta sync.**
- Auth via Credential Manager, client-side only.

**Guard the restore path hard.** The "you have local entries and a backup exists" merge decision is the one place you can destroy someone's data. Once is enough to end the app.

Four failure states to handle, all as conditions rather than errors:
1. No network
2. Drive quota full
3. Permission revoked
4. **Backup missing** — a user can delete hidden app data from Drive settings without ever opening your app

---

## 6. Build timeline

**16 weeks at ~10 hrs/week (~160 hours).**

This is native Android from scratch while learning Kotlin, Compose, coroutines, Flow, Room, Gradle, and AlarmManager quirks simultaneously. Eight weeks was the React Native estimate; do not carry it over.

| Weeks | Work |
|---|---|
| 1–2 | Project setup, Compose fundamentals, design system → theme code |
| 3–4 | Room schema, DAOs, repository, **date engine + full test suite** |
| 5–7 | List, add/edit sheet, detail, search — the CRUD core |
| 8–9 | FX fetch and capture, charge materialization worker, normalization |
| 10 | Insights, CSV export |
| 11–12 | Alarms, notification permission flow, OEM diagnostic, Glance widget |
| 13 | RevenueCat, paywall, purchase states |
| 14 | Drive backup and restore |
| 15 | Import CSV, polish, accessibility pass |
| 16 | Store assets, closed testing, launch |

### Parallel track — start week 1

Play Console registration and tester recruitment are a **calendar constraint, not a work constraint.** They run alongside the build.

| Week | Play track |
|---|---|
| 1 | Register account, start recruiting 12 testers |
| 2–4 | Keep recruiting until 12 confirmed |
| 5 | **Closed testing starts — 14-day clock begins** |
| 6 | Testers active, collect feedback |
| 7 | 14 days complete → apply for production access |
| 8+ | Production access granted, continue building |

Starting the closed test early with an incomplete build is fine and correct. The requirement is 12 testers opted in for 14 continuous days, not a finished product.

### Cut-list if you fall behind

Drop in this order: calendar view → insights charts (ship numbers as a list) → light theme → Drive backup (v1.1) → import CSV (v1.1).

**Never cut:** the add/edit sheet quality, date engine correctness, CSV export, or reminder reliability. Those four are the product.

---

## 7. Play Store setup

### Account

- **$25 one-time.** Register immediately — identity verification can take days.
- **Check organization eligibility first.** If BytWear is a registered business with a D-U-N-S number, an organization account is **exempt from the 12-tester requirement entirely.** This could save weeks. Verify before defaulting to personal.
- Personal accounts must run closed testing with **12+ testers continuously opted in for 14 days** before applying for production access.

### Tax and payments

- Google is merchant of record in most territories — you receive a single consolidated payout.
- Play service fee: **15% on your first $1M** annually.
- Export of services from India is zero-rated for GST but **requires an LUT filing.**
- FEMA reporting obligations apply to foreign receipts.
- **Talk to a CA before your first payout.** Not after.

### Store listing

| Field | Content |
|---|---|
| Title (30) | `Toolbill — SaaS Subscription Tracker` |
| Short description (80) | Carries heavy keyword weight on Play, unlike iOS. Write it deliberately. |
| Long description | Keyword density matters more on Play than the App Store. Write for humans and the crawler. |
| Screenshots | First two decide conversion. Lead with **"Never asks for your bank login"** and the normalized monthly total. |

Data safety form: you collect nothing. That is a marketing asset — say so loudly.

### Pre-launch requirements

- Privacy policy (GitHub Pages is fine)
- Support page or email
- Content rating questionnaire
- Target API level compliance

---

## 8. Business model

### The demographic problem — read this before committing

Your ideal user — freelancers and solo founders in the US, UK, and EU paying for 30 tools and earning in dollars — is **disproportionately on iPhone.** The indie hacker, designer, and consultant demographic skews heavily iOS.

Three responses:

**A. Android-first as a learning vehicle, iOS as the business.**
Ship Android, learn Compose and the domain, port to iOS in 2027 where the money is. Year one is tuition with modest revenue.

**B. Retarget to Android-heavy freelancer markets.**
India, Brazil, Indonesia, Poland, Vietnam, Nigeria. Enormous freelancer populations, Android-dominant, and — critically — **they** are the ones paying USD for tools while earning in a weaker currency. Your FX drift feature is dramatically more valuable to an Indian freelancer paying AWS in dollars than to an American one. ARPU drops: ₹499/year is realistic in India, not ₹1,499.

**C. Both, via regional pricing.** ← recommended
Play Console supports this natively. $19 in US/UK/EU, ₹499 in India, tiered elsewhere.

Option B deserves serious consideration rather than being the consolation prize. Your FX story is genuinely differentiated for that segment, you understand the market, and nobody is building for them.

### Unit economics

| Item | Cost |
|---|---|
| Play Console | $25 one-time |
| Domain (.app) | ~$15/year |
| RevenueCat | Free under $2.5K monthly tracked revenue |
| FX API (Frankfurter) | Free |
| Servers | None |
| **Annual fixed cost** | **~$15** |

$19 gross → $16.15 after Play's 15%, before Indian tax.

### Revenue projection — year one

Zero marketing budget, Play organic plus community posts.

| Metric | Conservative | Decent |
|---|---|---|
| Installs | 1,500 | 6,000 |
| Activation (≥3 subs added) | 35% | 45% |
| Free → paid | 2% | 4% |
| Payers | 30 | 240 |
| Blended revenue/payer | ~$22 | ~$24 |
| Gross | $660 | $5,760 |
| **Net after Play fee** | **~$560** | **~$4,900** |

The decent column is roughly $400/month by month twelve. Real side income that compounds if retention holds. **Not a job.** Plan your motivation around the conservative column.

---

## 9. Launch plan

ASO is the primary channel and mostly a one-time setup. Community distribution is the accelerant.

### Order of operations, first month post-launch

1. **Ship quietly.** Fix the first crash reports before telling anyone.
2. **Build-in-public thread** on X. You already have the BytWear muscle for this.
3. **Indie Hackers launch post** — framed as "I built this because my spreadsheet was lying to me." Lead with the normalization bug, not the feature list.
4. **Reddit:** r/SaaS, r/indiehackers, r/freelance, r/androidapps. Respect each sub's self-promotion rules.
5. **Product Hunt** once you're above 4.5 stars — not before.
6. **Directories:** AlternativeTo, indie app lists, Android newsletters.

### Ongoing

**Reply to every review for the first six months.** It's a genuine Play ranking factor and almost nobody does it.

---

## 10. Metrics and kill criteria

Track four numbers. Ignore everything else.

1. **Installs**
2. **Activation rate** — added ≥3 subscriptions
3. **Free → paid conversion**
4. **30-day retention**

### Kill criteria — decide now, while you're not emotionally invested

| Checkpoint | Threshold | Action |
|---|---|---|
| Month 3 | Activation below 30% | The add flow is broken. Fix it before anything else. |
| Month 6 | Under 500 installs total | ASO isn't working — wrong keywords, or the category has no organic search volume. Reassess before building features. |
| Month 12 | Under $500 net revenue | The thesis is wrong. Port to iOS or stop. **Do not spend a second year.** |

---

## 11. Open decisions

Three things the design doesn't resolve. Settle each before the relevant week.

### How does a charge get recorded? — decide by week 8

The design shows "mark paid?" on overdue rows, implying manual confirmation. But a user who ignores the app for a month returns to twelve unconfirmed charges.

- **Manual:** accurate, high friction, risks abandonment
- **Auto-materialize on renewal date, user corrects:** low friction, risks silent inaccuracy

Recommendation: auto-materialize, with the row visually marked "unconfirmed" until the user acknowledges. Preserves the low-friction path while flagging what hasn't been verified.

### Is the widget free or Pro? — decide by week 12

The paywall lists four Pro features and the widget isn't among them, so it currently reads as free. **Keep it free.** It's a retention driver and a reason to open the app; gating it hurts more than it earns.

### Annual-vs-monthly savings needs data you don't have — decide by week 10

The comparison requires knowing a subscription's annual price. Options: user enters it manually, or you bundle it in the catalogue (where it goes stale, same problem as amounts).

Simplest v1: only surface the comparison when the user has entered both figures.

---

## 12. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| OEM battery policy kills reminders | **High** | **Severe** — core feature silently fails | Layered alarms + WorkManager net + diagnostic screen + test on real Xiaomi/Realme |
| Play 12-tester rule delays launch | Medium | Moderate — 2–4 week slip | Start week 1; check organization-account exemption first |
| Target audience is on iPhone | **High** | **Severe** — ceiling on the whole thesis | Regional pricing; consider Android-heavy markets as primary |
| Learning curve blows the timeline | Medium | Moderate | 16 weeks already assumes it; use the cut-list |
| Date engine bugs corrupt totals | Medium | **Severe** — destroys the one thing the app sells | Pure functions, tests first, JVM-testable |
| Drive restore destroys data | Low | **Severe** — unrecoverable trust loss | Guard the merge path; never auto-overwrite local |
| Stale bundled catalogue prices | Certain if shipped | Moderate | Already resolved — don't ship amounts |
| Nobody searches for this on Play | Medium | Severe | Month-6 kill criterion catches it |

---

## 13. Pre-flight checklist

Before writing any Kotlin:

- [ ] Apply Fixes K, L, M to the design document (8-item footer, type specimen, changelog wording)
- [ ] Name clearance: Play Store, App Store, USPTO TESS, IP India class 9/42, domain
- [ ] Register Play Console account ($25)
- [ ] **Check whether BytWear qualifies for an organization account** — exempts you from the 12-tester rule
- [ ] Start recruiting 12 testers
- [ ] Verify the App Store and Play Store have no strong existing "SaaS subscription tracker" at ~$19/yr
- [ ] List your own 20+ subscriptions across BytWear, AdStockGuard, personal — confirm you're the user
- [ ] Create RevenueCat project
- [ ] Initialize repo, set up CI (GitHub Actions running unit tests on push)

Week 1's first commit should be `nextChargeDate()` and its test suite. Not the theme, not the navigation graph. The date engine is the correctness core, and everything downstream is wrong if it's wrong.

---

## Appendix — one honest note

The risk isn't that Toolbill fails. It's that it takes 16 weeks plus a year of maintenance to reach $400/month while AdStockGuard — a live Shopify app with real merchant installs and a distribution channel already solved — sits neglected.

You've never shared its numbers. If it has installs and weak revenue, that's a monetization and positioning problem solvable in weeks, against a cold Android launch that the data above puts at 12–18 months.

Build Toolbill because you want to learn native Android and own something you use daily. Those are good reasons and they're sufficient. Just don't let it become the reason the thing that already works stays untouched.
