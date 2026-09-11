package com.toolbill.android.feature.`import`

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.toolbill.android.UserSettings
import com.toolbill.android.core.data.repository.ChargeRepository
import com.toolbill.android.core.data.repository.SubscriptionRepository
import com.toolbill.android.core.domain.importing.ColumnMapping
import com.toolbill.android.core.domain.importing.ImportField
import com.toolbill.android.core.domain.importing.ImportPreview
import com.toolbill.android.core.domain.importing.Table
import com.toolbill.android.core.domain.importing.guessMapping
import com.toolbill.android.core.domain.importing.parseImport
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import com.toolbill.android.core.domain.subscription.pricedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What the four import screens are looking at. */
data class ImportState(
    val fileName: String? = null,
    val table: Table? = null,
    val mapping: ColumnMapping = ColumnMapping(),
    val preview: ImportPreview? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val result: ImportOutcome? = null,
)

/** What an import actually did, for the last screen. */
data class ImportOutcome(
    val imported: Int,
    val skipped: Int,
    val burnBeforeMinor: Long,
    val addedMonthlyMinor: Long,
)

/**
 * Holds one import from the file being chosen to the rows landing.
 *
 * Scoped above the four screens rather than to any one of them, so going back a step does not
 * throw away a parse and make the user pick the file again.
 */
class ImportViewModel(
    private val repository: SubscriptionRepository,
    private val chargeRepository: ChargeRepository,
    private val settings: UserSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Clears everything, so a second import does not begin inside the first one's results. */
    fun reset() {
        _state.value = ImportState()
    }

    fun load(context: Context, uri: Uri) {
        _state.value = ImportState(busy = true)
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { ImportSource.displayName(context, uri) }
            withContext(Dispatchers.IO) { ImportSource.read(context, uri) }.fold(
                onSuccess = { table ->
                    _state.value = ImportState(fileName = name, table = table)
                    setMapping(guessMapping(table.headers))
                },
                onFailure = { failure ->
                    _state.value = ImportState(
                        fileName = name,
                        error = failure.message ?: "That file could not be read.",
                    )
                },
            )
        }
    }

    fun assign(field: ImportField, columnIndex: Int?) {
        setMapping(_state.value.mapping.with(field, columnIndex))
    }

    /** Re-parses on every mapping change, so the review screen is never a step behind. */
    private fun setMapping(mapping: ColumnMapping) {
        val table = _state.value.table ?: return
        viewModelScope.launch {
            val preview = if (mapping.isUsable) {
                val existing = repository.subscriptionsOnce()
                withContext(Dispatchers.Default) {
                    parseImport(table, mapping, settings.homeCurrency.value, LocalDate.now(), existing)
                }
            } else {
                null
            }
            _state.value = _state.value.copy(mapping = mapping, preview = preview, error = null)
        }
    }

    /** Ticks or unticks one row. */
    fun toggle(rowNumber: Int) {
        val preview = _state.value.preview ?: return
        val updated = preview.copy(
            candidates = preview.candidates.map { candidate ->
                if (candidate.rowNumber == rowNumber && candidate.isUsable) {
                    candidate.copy(include = !candidate.include)
                } else {
                    candidate
                }
            },
        )
        _state.value = _state.value.copy(preview = updated)
    }

    /**
     * Writes the selected rows.
     *
     * The ids are recorded so the whole import can be taken back for a day: thirty rows landing
     * at once is the largest single change this app can make to someone's data, and the moment
     * to notice a wrong column mapping is after seeing the burn figure move, not before.
     */
    fun commit() {
        val preview = _state.value.preview ?: return
        val selected = preview.selected.mapNotNull { it.subscription }
        if (selected.isEmpty()) return

        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val home = settings.homeCurrency.value
            val burnBefore = repository.subscriptionsOnce()
                .filter { it.status.countsTowardBurn }
                .sumOf { normalizedMonthlyMinor(it.pricedIn(home).homeAmountMinor, it.cycle) }

            repository.insertAll(selected)
            settings.recordImport(selected.map { it.id })

            // The new rows may already have charges due; record them now so their detail screens
            // have history immediately rather than after the next cold start.
            runCatching { chargeRepository.recordDueCharges(LocalDate.now(), home) }

            val added = selected.sumOf {
                normalizedMonthlyMinor(it.pricedIn(home).homeAmountMinor, it.cycle)
            }
            _state.value = _state.value.copy(
                busy = false,
                result = ImportOutcome(
                    imported = selected.size,
                    skipped = preview.candidates.size - selected.size,
                    burnBeforeMinor = burnBefore,
                    addedMonthlyMinor = added,
                ),
            )
        }
    }

    companion object {
        fun factory(
            repository: SubscriptionRepository,
            chargeRepository: ChargeRepository,
            settings: UserSettings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImportViewModel(repository, chargeRepository, settings) }
        }
    }
}
