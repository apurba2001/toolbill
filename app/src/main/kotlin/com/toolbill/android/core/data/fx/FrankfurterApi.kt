package com.toolbill.android.core.data.fx

import com.toolbill.android.core.domain.money.FxRateTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The wire shape of `GET /v1/latest`.
 *
 * `date` is the date the rates were *published for*, not the date they were fetched. The ECB
 * publishes on business days, so a Sunday refresh returns Friday's rates — and the app says so
 * rather than stamping today's date on figures that are two days old.
 */
@Serializable
private data class LatestRates(
    @SerialName("base") val base: String,
    @SerialName("date") val date: String,
    @SerialName("rates") val rates: Map<String, Double>,
)

/**
 * Exchange rates from Frankfurter — ECB reference rates, free, no key, no account.
 *
 * The only network call this app makes. It carries currency codes and nothing else: no
 * identifier, no subscription, no amount. That is what lets the privacy claim on the onboarding
 * screen stay literally true while the rates are still current.
 */
class FrankfurterApi(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * Fetches rates for [symbols], expressed the way [FxRateTable] holds them.
     *
     * Returns a failure rather than throwing: a rate refresh is the one thing in this app that
     * depends on a network, and every caller has a perfectly good answer for not getting one —
     * keep using the rates already in hand.
     */
    suspend fun fetchLatest(symbols: Collection<String>): Result<FxRateTable> =
        withContext(Dispatchers.IO) { fetchBlocking(symbols) }

    /**
     * The call itself, which blocks.
     *
     * Main-safe at the boundary above rather than at each call site: a worker and an app-start
     * coroutine were already on IO and worked, so the one caller that was not -- a tap in
     * Settings -- was the only place this threw, and it threw NetworkOnMainThreadException where
     * a user would read it as "could not reach the rate source".
     */
    private fun fetchBlocking(symbols: Collection<String>): Result<FxRateTable> = runCatching {
        val wanted = symbols.map { it.uppercase() }.filterNot { it == BASE }.sorted()
        require(wanted.isNotEmpty()) { "nothing to fetch" }

        val url = "$ENDPOINT?base=$BASE&symbols=${wanted.joinToString(",")}"
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("Frankfurter returned ${response.code}")
            response.body?.string() ?: error("Frankfurter returned an empty body")
        }

        val parsed = json.decodeFromString<LatestRates>(body)

        // The response gives units of X per one INR; the table holds INR per one X. Inverted
        // here rather than at every call site, and at a scale that survives a currency whose
        // rate against the rupee is a small fraction.
        val inrPer = buildMap {
            put(BASE, BigDecimal.ONE)
            parsed.rates.forEach { (code, perInr) ->
                if (perInr > 0.0) {
                    put(
                        code.uppercase(),
                        BigDecimal.ONE.divide(BigDecimal.valueOf(perInr), 10, RoundingMode.HALF_UP),
                    )
                }
            }
        }
        require(inrPer.size > 1) { "Frankfurter returned no usable rates" }

        FxRateTable(
            inrPer = inrPer,
            capturedOn = LocalDate.parse(parsed.date),
            liveCodes = inrPer.keys,
        )
    }

    private companion object {
        const val ENDPOINT = "https://api.frankfurter.dev/v1/latest"

        /** The table's internal base. See [FxRateTable]. */
        const val BASE = "INR"

        val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // Short: this runs on a background worker with nothing waiting on it, and a request
            // left hanging on a flaky connection is a wakelock held for no benefit.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
