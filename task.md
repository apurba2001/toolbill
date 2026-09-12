# Toolbill — build log

## Done

### Foundation (weeks 1–9 of TOOLBILL_PLAN.md)
- [x] Room dependencies and KSP plugin
- [x] `SubscriptionEntity` and `ChargeEntity`
- [x] `DatabaseConverters` for domain primitives
- [x] `SubscriptionDao` and `ChargeDao`
- [x] `ToolbillDatabase`
- [x] `SubscriptionRepository`
- [x] `SubscriptionDaoTest` — instrumented, covers the cascade and `createdAt` behaviour
      that `OnConflictStrategy.REPLACE` broke
- [x] Connect `AddEditSubscriptionSheet` to Repository
- [x] Connect `AllSubscriptionsScreen` to Repository
- [x] Connect `HomeScreen` to Repository
- [x] Connect `CalendarScreen` to Repository
- [x] Connect `InsightsScreen` to Repository
- [x] Connect `SubscriptionDetailScreen` to Repository
- [x] Connect `ToolbillWidget` to Repository
- [x] Record charges — `ChargeRepository.recordDueCharges`
- [x] Derive every figure in one place — `core/domain/subscription/Portfolio.kt`
- [x] Persist the home currency onboarding asks for, and onboarding completion itself
- [x] Move `SampleData` to the test source set so it cannot reach a screen

### Phase 1 — stop the app from lying
Every long-press action used to show a confirming snackbar and write nothing: "Deleted Claude"
left the row in place. The screens now call through to real writes, and every one of them
carries its own inverse.

- [x] `SubscriptionsViewModel` gained `markPaid`, `skipCharge`, `togglePause`, `duplicate` and
      `deleteWithUndo`; each emits an `Undoable` carrying the way back
- [x] `AllSubscriptionsScreen` takes typed callbacks instead of the fire-and-forget
      `onAction(String, String)`
- [x] Undo is real — `ToolbillApp` collects `undoables` and calls `undo()` on
      `SnackbarResult.ActionPerformed`
- [x] Edit opens the edit sheet. It used to push the detail screen, which is what a plain tap
      already does
- [x] Pause reads Resume on a paused row
- [x] **Recorder no longer overwrites existing charges.** It upserted every due date on every
      run, which reverted any charge the user marked skipped and re-stamped every captured home
      figure at today's rate — destroying the one thing the charges table exists to preserve
- [x] Delete captures the row *and* its charges before the cascade runs, so Undo restores the
      whole record, `createdAt` included
- [x] Diagnostics self-checks read real system state (notification permission, battery
      optimization) and re-read on resume; device name comes from `Build`; the battery and
      notification buttons open the real settings pages. The five hardcoded checks are gone —
      one of them claimed "Notification permission: granted" on a build that has never asked
- [x] "Send test reminder" disabled and labelled honestly until reminders exist
- [x] Settings: "Undo last import" removed — it described an import that never happened
      ("27 subscriptions, 2 hours ago"). About reads `BuildConfig.VERSION_NAME` and states the
      tier actually held, not the hardcoded "1.4.0 · Pro (lifetime)"
- [x] 7 new instrumented tests; 20 instrumented and 95 JVM tests green, no crash on launch

### Phase 2 — charge schema v2→v3
The charges table could not say what rate a charge was converted at, so the CSV export had no
source for its rate column and drift had nothing to measure against.

- [x] Room schema export turned on, and the v2 schema captured *before* the change so
      `MigrationTestHelper` can build a real old database and run the real migration over it
- [x] `ChargeEntity.fxRate: BigDecimal?` — the rate applied at record time, stored as TEXT
      rather than REAL, because a rate is money arithmetic
- [x] `ChargeEntity.isUserOverridden` — marks rows a person set, so a later rate correction can
      sweep the recorder's figures and leave hand-entered ones alone
- [x] `MIGRATION_2_3`; existing rows get a null rate rather than a back-filled guess
- [x] `FxRates.rateFor()` exposes the rate the conversion used
- [x] A skipped charge now keeps its home currency and records a known zero with no rate — it
      was recording as "not captured", which is a different thing and renders as a dash
- [x] **Skipped charges excluded from the detail total, the drift chart and the Insights FX
      figure.** A zero-cost row with a non-zero contracted amount set a baseline rate of zero
      and took the whole drift calculation with it
- [x] Serialization alignment forced on the instrumented classpath — `room-testing` reads schema
      JSON through kotlinx-serialization, and a platform constraint pinning core to 1.7.3
      against Room's json 1.8.1 threw `AbstractMethodError` from a generated serializer
- [x] 2 migration tests, 2 charge tests, 3 FX tests. 24 instrumented and 98 JVM tests green;
      v3 schema verified on device, no crash on launch

### Phase 3 — reminders
The one never-cut feature that did not exist at all. No WorkManager, no permissions, no channel,
no receivers, no scheduling code — and a rationale sheet whose Allow button only closed it.

- [x] `core/domain/reminder/ReminderPlan.kt` — the whole scheduling decision as pure functions,
      pinned by 15 JVM tests. What to remind, when, what is overdue, what has been delivered
- [x] **One alarm for the portfolio, not one per subscription.** Thirty-five alarms is thirty-five
      things for a battery optimiser to drop; one is a single point the safety net can check
- [x] `AlarmManager.setWindow` with an hour of tolerance — no `SCHEDULE_EXACT_ALARM`, which Play
      restricts to alarm clocks and calendars and this app could not justify
- [x] One entry point, `ReminderManager.refresh()`, shared by app start, the alarm, a reboot, a
      clock or timezone change, the daily worker and every data change. Firing is idempotent by
      construction: when the alarm goes off, its reminders are already what `overdueReminders`
      returns, so there is no separate delivery path to drift out of step
- [x] Re-planning is driven off the data flow rather than called after each write — a row can
      change through the sheet, a row action, an undo or a restore, and wiring five call sites
      is five chances to miss one
- [x] `RescheduleReceiver` for BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED
- [x] `ReminderWorker` daily safety net through WorkManager — a different mechanism with
      different constraints, so an OEM policy that drops the alarm does not drop both
- [x] Delivery keyed per charge, so a renewal is announced once and next month's still gets
      through. Nothing is recorded as delivered unless it could actually be delivered
- [x] `POST_NOTIFICATIONS` requested for real, after the first entry, once per install
- [x] Lead time is a live control (1/2/3/5/7 days) — changing it re-plans, because a longer lead
      can make a reminder owed that was not owed a moment ago
- [x] Test reminder re-enabled and real; `lastReminderFiredAt` written, so the diagnostic
      self-check reports delivery rather than asserting it
- [x] `AlarmManager.cancel` leaves the PendingIntent registered — caught by a test, so cancelling
      now releases it too and "nothing scheduled" is observable
- [x] Two layout bugs found on device: a real device name ("Google sdk_gphone16k_x86_64 · Android
      17") wrapped the DETECTED DEVICE eyebrow one letter per line, and the two action buttons
      broke mid-word at phone width. Both stacked now
- [x] **Verified end to end on device** — added Claude Pro, notification delivered reading
      "Claude Pro · Renews today · $20.00", self-check reporting granted / restricted / just now
- [x] 113 JVM and 31 instrumented tests green

### Phase 4 — CSV export
The export screen rendered seven invented rows and a summary reading "112 charges · 32 services"
on an install that had none — so the one screen whose whole job is to state exactly what it will
hand an accountant was the least accurate in the app. There was no file I/O anywhere in `main`.

- [x] `core/domain/export/ChargeCsv.kt` — period, filtering, row generation and summary as pure
      functions, pinned by 17 JVM tests
- [x] **RFC 4180 quoting.** A service name like "Adobe, Inc." would otherwise shift every column
      after it by one, silently, in a file nobody re-reads before filing it
- [x] **ISO-8601 dates**, departing from the design's `26-08-27` preview — that is `DD-MM-YY` to
      half the world, and a filing cannot carry the ambiguity to save four characters
- [x] Money written in major units at the currency's own scale — what a spreadsheet sums
- [x] Financial year computed from the run date (Apr–Mar), not hardcoded to 2026-27; quarter is
      the calendar quarter; custom range gets real date pickers, and picking a start after the
      end pushes the far end rather than refusing the date just chosen
- [x] Skipped charges export as explicit zero rows — a gap in a monthly sequence is something an
      accountant has to chase, an explicit zero is something they can read
- [x] Cancelled is an addition, not a third category: a cancelled business tool is still
      deductible business spend
- [x] `ACTION_CREATE_DOCUMENT` to save (Drive, Dropbox and a USB stick are all destinations in
      the storage picker) and a `FileProvider` share intent, staged in cache so a copy of
      someone's spend history is not left in a world-readable directory
- [x] "Save to Drive" relabelled "Save as file" — it saves anywhere, and the old label would
      have been read as the Drive backup feature that does not exist yet
- [x] **Verified end to end on device**: saved file reads
      `2026-09-11,Claude Pro,AI,,biz,20.00,USD,1,20.00,paid` under the fixed header
- [x] A `--` inside an XML comment broke the manifest parse; caught immediately, all comments
      checked

### Also fixed, found while verifying
- [x] **"Google Drive backup — Synced 2 hours ago · 74 KB", switch on.** Missed in Phase 1
      because it is rendered inline rather than in the row list. A switch that claims your data
      is safe and is wired to nothing is the most costly placeholder this app could carry
- [x] The notification rationale sheet previewed "renews in 3 days — ₹1,742" whatever the user
      had chosen. Now drawn in their own currency and lead time, with the amount converted from
      a real 20 USD rather than restated
- [x] "1 charges · 1 services" — a summary that reads as a bug undermines the figure beside it
- [x] 132 JVM and 31 instrumented tests green

### App icon
The app shipped with no launcher icon at all — Android was drawing the stock robot, which is
also what appeared as the avatar on every renewal reminder.

- [x] The mark: one amber rule with three bars beneath it. The rule is the normalized monthly
      figure, in the same amber every hero number on every screen is drawn in; the bars are the
      subscriptions that make it up, at different lengths because they cost different amounts on
      different cycles. No dollar sign, no coin, no mascot — the positioning is explicit that
      this is a tool, and its user runs Linear and Raycast
- [x] Adaptive icon with background, foreground and a monochrome layer for Android 13+ themed
      icons. minSdk is 26, so there are no legacy PNG densities to keep in step
- [x] **Geometry fits a circle, not the safe box.** The first cut sat inside the usual 72x72
      guidance and still had the rule's rounded ends sliced flat by the Pixel launcher's circular
      mask — the corners were 36.5 from centre against a 36 radius. Everything now sits within 33
- [x] Dedicated `ic_notification` silhouette, redrawn rather than reusing the launcher geometry:
      that carries adaptive padding and would render as a blob adrift in the status bar. Bar gaps
      widened well past a pixel so they survive the downscale — verified legible at status-bar size
- [x] Quick-settings tile no longer borrows `@android:drawable/ic_menu_add`
- [x] `design/play-store-icon-512.png` for the listing, rendered through the same 72-of-108 crop
      the launcher applies so it matches the device rather than looking smaller on the store
- [x] Verified on device: launcher, app drawer and status bar

### Phase 5 — live FX
The differentiator, and until now a stub: one static table with a single constant per currency,
so drift was zero by construction and the FX sections correctly showed nothing.

- [x] `FxRateTable` — rates and their publication date as pure, immutable data; all conversion
      math moved onto it and pinned by 10 JVM tests
- [x] `FxRates` holds one swappable table behind a `StateFlow`. Conversion happens inside `map`
      blocks on the subscription list, in the widget and in the charge recorder — making it
      suspending would have pushed a coroutine through every one of them for a hash-map read.
      The data layer swaps the whole table in one assignment instead
- [x] The subscription list combines against that flow, so a refresh moves every figure on every
      screen without a single stored row changing
- [x] `FrankfurterApi` — ECB rates, free, no key. The request carries currency codes and nothing
      else: no identifier, no subscription, no amount
- [x] **Live rates merge over the bundled table rather than replacing it.** The ECB publishes
      thirty currencies and AED is not among them — verified against the live API, which returns
      200 and silently omits it. A refresh that replaced the table would take a currency the add
      sheet still offers out of service
- [x] `fx_rates` table (migration 3→4) keeps one row per currency per published date, so a charge
      recorded late is priced at the rate in force on the day it fell due. `ratesInForceOn` falls
      back to the last publication before the date, which is what a card would have been charged
      at over a weekend
- [x] Three sources in descending order of currency — live fetch, last fetch on disk, bundled.
      A device offline for a week prices at last week's real rates, not April's
- [x] Daily `FxRefreshWorker` on a network constraint; the ECB publishes once a business day, so
      polling harder would spend battery to fetch the same numbers back
- [x] Settings states the source and the real publication date, and says "bundled" rather than
      claiming live figures on an install that has never reached the network
- [x] **FX drift rewritten.** It derived its baseline by dividing one rounded figure by another
      in `Double` — against this app's own rule that money is never a float — and included
      home-currency charges, which have no movement to measure and inflated both sides of a
      comparison under a heading that says FX. Now reads the stored `fxRate` and counts only
      foreign-billed charges
- [x] "Tell me about FX drift over 3% - Rs 500" removed: it promised a notification nothing
      sends, and quoted rupees whatever the home currency was
- [x] **Verified end to end on device**: $20/mo reads ₹1,908.40 at the live rate where the
      bundled table gives ₹1,742.00; Settings reads "European Central Bank, published 10 Sep
      2026 · live"; AED still offered in the picker
- [x] 142 JVM and 39 instrumented tests green


### Phase 6 — CSV import
Four screens of invented data: a fixed preview, "25 ready / 5 need a look", "27 imported". No
file was ever read — there was no file I/O in the app at all until Phase 4.

- [x] `core/domain/importing/` — parser, mapping and row conversion as pure functions, pinned by
      33 JVM tests
- [x] **RFC 4180 parsing, not a split.** A spreadsheet quotes `"Adobe, Inc."`, escapes quotes by
      doubling them, and wraps a note containing a line break across two lines. Splitting on the
      delimiter would shift every later column of those rows, silently
- [x] Delimiter detected by consistency across the first lines — a European export is
      semicolon-delimited because the comma is its decimal separator
- [x] Ragged rows padded and truncated to the header count; a leading byte-order mark stripped,
      or Excel's first heading arrives as `﻿Service` and matches nothing
- [x] **Date order decided from the whole column.** `03/04/2026` is 3 April to most of the world
      and 4 March in the US, and guessing wrong moves every renewal by up to eleven months. One
      value with a first number above twelve settles it for the rest
- [x] Amounts read in both separator conventions, with the rule that three digits after a lone
      separator is a thousands group — `1,299` is not one rupee and 299 paise
- [x] Currency from a code or a symbol, cycles from the words people actually write
      ("every 3 months", "fortnightly", "per year"), categories by name or export code
- [x] Column guessing is scored and each column claimed once, so "Monthly cost" goes to Amount
      rather than being taken by Billing cycle — verified on device
- [x] **Every row appears in the review, including the ones that cannot be imported and why.**
      Dropping them silently means importing thirty rows, getting twenty-seven, and having no way
      to find out which three went missing
- [x] A likely duplicate arrives unticked: importing the same spreadsheet twice is the one
      mistake whose result still looks plausible, because the burn figure just gets bigger
- [x] Transactional insert; the result screen shows the burn before and after, which is how a
      column mapped wrongly actually gets noticed
- [x] **"Undo last import" restored** — removed in Phase 4 because it described an import that
      never happened, and now backed by one. Deletes only the ids that import wrote, so anything
      added since is untouched
- [x] JSON accepted as well as CSV/TSV: an array of flat objects, keys unioned into headings
- [x] `ImportScreen` had its action buttons duplicated in the source; fixed
- [x] **Verified end to end on device** with a deliberately awkward file: quoted comma, `$20`
      with an empty currency column, "every 2 months", a missing date, an unparseable amount and
      a nameless row. 5 imported, 2 refused with reasons, burn $0.00 → $54.42
- [x] 175 JVM and 39 instrumented tests green


### Phase 7 — entitlement seam (billing itself is blocked)
The paywall took a plan selection and a "Continue" tap and did nothing with either. It also
quoted ₹1,499 to every user regardless of region, and offered Terms, Privacy and Restore buttons
wired to nothing.

**What is blocked and why.** Play Billing needs a Play Console with configured products, and
RevenueCat needs an account. Neither exists for this build, so no purchase can complete. Rather
than gate four working features behind a checkout that cannot take money — and break reminders
and export for the closed-testing group who have to test them — the gates are built, wired and
resolving to granted, with the reason stated in the UI.

- [x] `core/billing/Entitlement.kt` — `Tier`, `ProFeature`, `EntitlementReason` and an
      `EntitlementSource` interface. Swapping the implementation is the whole of what billing
      needs to change; every gate already reads through it
- [x] `ProFeature` names exactly the four the design gates. The widget is deliberately absent —
      the plan's week-12 decision keeps it free as a retention driver
- [x] Reminders consult the gate **at delivery, not at scheduling**, so a lapse stops the
      notifications without losing the schedule — the design is explicit that nothing is deleted
      or hidden on lapse
- [x] `EarlyAccessEntitlements` grants Pro and says why. It is not a stub pretending a purchase
      happened; nothing in the app claims one did
- [x] Paywall reads the entitlement: no checkout while there is nothing to sell, and it says so
      — "the prices shown are indicative, Play will set them for your region". Restore, Terms and
      Privacy removed rather than left dead
- [x] "Restore purchases" removed from Settings for the same reason; About reads the real tier
      and now shows "0.1.0 · Pro · early access"
- [x] 6 tests covering both sides of the gate, so the free branch is exercised before the day it
      is switched on. `kotlinx-coroutines-test` added to the JVM test path
- [x] 181 JVM and 39 instrumented tests green; verified on device


### Phase 9 — launch hardening
- [x] **The back stack survives process death.** `Screen.Detail` carries the subscription it was
      opened for, and the whole stack was a plain `remember` — the system can kill the process
      while the user is in another app, and coming back to the list instead of the row they were
      reading is the kind of loss nobody reports and everybody notices. Now a `rememberSaveable`
      with a token-based saver, **verified by killing the process on device** and watching it
      return to the same detail screen
- [x] The import wizard is deliberately dropped from the restore: its four steps hang off a
      parsed file held in a view model, and restoring someone to step three of an import whose
      rows no longer exist is worse than putting them back where they started
- [x] `targetSdk` 37, level with `compileSdk`. The Android 17 changes this app can touch are
      edge-to-edge and predictive back, both already opted into; it runs no foreground service,
      starts no activity from a notification, and holds no exact-alarm permission. Verified on an
      API 37 device
- [x] **Catalogue: 9 entries → 210**, moved to `core/domain` and covered by tests — including one
      asserting the type cannot carry a price, since a bundled price is stale the day it ships.
      Suggestions rank prefix matches above contains matches, so "not" offers Notion first while
      "cloud" can still find iCloud+
- [x] GitHub Actions CI: unit tests and lint on every push, per the plan's pre-flight checklist.
      Instrumented tests deliberately left off the per-push job
- [x] **Lint: 8 errors → 0.** Two were real — `NotificationManagerCompat.notify` had no explicit
      permission check (`canNotify` now checks the runtime permission, not just whether
      notifications are enabled), and two raw byte-order marks sat invisibly in `Csv.kt`
- [x] Explicit `dataExtractionRules`: nothing leaves the device automatically, for cloud backup
      or device transfer. Backup is something the user turns on, to their own Drive
- [x] Quick-settings tile deprecation suppressed with its reason; obsolete `-v26` mipmap
      qualifier dropped
- [x] **"₹ / MONTH" column header was hardcoded** — on a GBP install it labelled pounds as
      rupees. Reads the home currency's own symbol now, verified showing "$ / MONTH"
- [x] Categories lost its chevron: the categories are fixed by design, so the arrow promised a
      screen that never existed
- [x] 193 JVM and 39 instrumented tests green

### Phase 8 — backup and restore
The risk register rates "Drive restore destroys data" Severe and unrecoverable, and the reason is
that a restore looks routine right up until it isn't. So the dangerous half was built first and
proven before any credentials exist — and because it is destination-agnostic, backup and restore
**work today, to a file**. Drive becomes another destination rather than a different feature.

- [x] **`updatedAt` added to subscriptions (migration 4→5).** The plan's data model always
      specified it; without it a restore can only overwrite, because nothing can tell an edit
      made on this phone from an older copy of the same row in the backup
- [x] Back-filled from `createdAt`, not the migration's clock. Stamping every row "now" would
      make the whole database look newer than any backup taken before the update, and the first
      restore after upgrading would silently discard it. Pinned by a migration test
- [x] `upsert` stamps `updatedAt` — a last-write-wins merge is only as good as the one field it
      compares, and a caller that forgot would make its row permanently lose
- [x] **The merge, with 16 tests.** Three rules: a row only in the backup is added; a row in both
      takes the newer copy and **a tie keeps local**; a row only on this device is **kept**. That
      third one is the whole point — a backup is a snapshot of one moment, not a statement about
      what should exist now, and deleting local rows because an older file omits them is exactly
      how a restore destroys data while reporting success
- [x] Charges are additive and never rewritten: each records something observed at a rate
      captured that day, so there is no "newer" version of one. A charge whose subscription will
      not exist is dropped rather than orphaned
- [x] Restore is two steps — work out the plan, show it, apply only on confirmation. The dialog
      states what will be **left alone**, because "will my own entries survive this?" is the
      question the user actually has
- [x] A backup from a newer version is refused outright: reading one as far as it parses is data
      loss wearing a success message. An empty backup is refused too, in both directions
- [x] Gzipped JSON with its own flat types, not the Room entities — the file outlives the code
      that wrote it. Plain JSON also accepted, so a file someone un-gzipped by hand still restores
- [x] `DriveAppData`: REST v3 `appDataFolder`, one file replaced in place, and all four failure
      states the plan names as conditions rather than errors — offline, quota full, access
      revoked, no backup. Waiting only on a token
- [x] **Verified end to end on device**: backed up 5 subscriptions and 1 charge (652 bytes),
      deleted a subscription, restored — dialog read "1 to add. Nothing on this device is
      deleted. 4 you have edited more recently than the backup are left as they are", and the
      burn returned to exactly A$75.71 across 5 active
- [x] 219 JVM and 40 instrumented tests green, 0 lint errors

## Device-reported fixes

Everything below came from the user testing builds on a Samsung SM-M066B (Android 16), not from
the plan. Each is listed with the cause, because the cause is what stops it recurring.

- [x] **App lock re-prompted after a file picker and after system settings.** The lock fired on
      every `onStop`, and the app cannot tell a user leaving from the app sending them out.
      `MainActivity` now latches on its own `startActivity` overrides -- all three -- so a trip
      the app initiated returns unlocked, and a trip the user initiated still locks. A 60s grace
      covers paths that never reach those overrides.
- [x] **Diagnostics offered fixes for permissions already granted.** The guidance is now gated on
      `needsAttention`; a healthy phone sees only the device line.
- [x] **"Send test" did not update "Last reminder fired".** It does now, suffixed " (test)" --
      unsuffixed would let the screen look healthy on a phone dropping every real renewal, which
      is the failure it exists to catch.
- [x] **Paywall drifted from the design.** Restored the header restore-purchase, the Continue CTA
      and the footer. Yearly and Lifetime are equal height and width via
      `Row(Modifier.height(IntrinsicSize.Min))` + `weight(1f).fillMaxHeight()`. Continue was
      missing because I had removed it, not because the app was sideloaded; tapping it now says
      so plainly rather than failing silently.
- [x] **Three Settings rows did nothing on tap.** `SettingItem` is only clickable when it has an
      action. Exchange rates is now tap-to-refresh; Categories and Paused are plain readouts.
      The first tap threw `NetworkOnMainThreadException` -- fixed in `FrankfurterApi` at the
      boundary, not the call site, so no future caller can reintroduce it.
- [x] **Search and filter showed the wrong empty state and dropped the chips.** The gate was on
      the filtered list; it is now on the unfiltered one, so an empty *result* and an empty
      *portfolio* are different screens. Search input restyled to the design.
- [x] **Edit sheet: cycle text wrapping, "1 Sep" twice, dead category dropdown, subscription
      absent from the calendar.** The duplicate chip and the empty calendar were one bug -- the
      preset resolved to next year; it is now the 1st of next month. Category opens a real
      picker.
- [x] **No-matches state looked unfinished.** Rebuilt in the app's own language: dimmed hero
      showing the burn the filter is hiding, a headline naming the cause, the count that is
      still there, and a "Show all N" button.

## Next

- [ ] **Phase 7 (blocked) — real billing.** Needs a Play Console with products
      and a RevenueCat account. Then: swap `EarlyAccessEntitlements` for a
      RevenueCat source, restore the purchase CTA, localized prices from Play,
      restore-purchases, and Terms/Privacy links.
- [ ] **Phase 8 (blocked) — the Drive transport.** `DriveAppData` is written and waiting for an
      access token; getting one needs a Google Cloud OAuth client and Credential Manager sign-in.
      Backup and restore already work to a file using the same payload and the same merge.
- [ ] **Phase 9 remainder — store assets.** Feature graphic (1024x500), screenshots and the
      long description. The 512 launcher icon is done; the rest needs the Play listing itself.

## Known and deliberate

- **Reminders are unverified on a real OEM device.** The plan's risk register rates this
  High/Severe: an emulator does not reproduce Xiaomi, Realme or Oppo battery policy, which is
  exactly where layered alarms get dropped. The design accounts for it (one alarm + a WorkManager
  net + an honest diagnostic screen) but only a physical device proves it.
- `ToolbillWidget` is wired but unverified on a launcher — adb cannot place a widget or send
  `APPWIDGET_UPDATE`, so it needs a manual check.
- `resumeDate` exists on the model and the list renders it when set, but pausing never asks for
  one, so it is always null and a paused row reads "not in burn". Letting pause capture a resume
  date is a small, real feature that is not built.
- `proguard-rules.pro` is intentionally empty. The release build was signed and exercised on
  device — see the file's own note for what was checked.
- Pro features are unlocked for everyone while there is nothing to buy. See Phase 7.
