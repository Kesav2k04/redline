package dev.kesav.redline

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Package
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScanState {
    data object Editing : ScanState

    data class Scanned(
        val clauseCount: Int,
        val findings: List<Finding>,
    ) : ScanState {
        val flaggedClauses: Int get() = findings.map { it.clause.index }.distinct().size
        val high: Int get() = findings.count { it.severity == Severity.HIGH }
    }
}

data class ScanUi(
    val text: String = "",
    val state: ScanState = ScanState.Editing,
    val unlocked: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val offer: Package? = null,
)

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(ScanUi())
    val ui: StateFlow<ScanUi> = _ui.asStateFlow()

    init {
        // Entitlement is read before anything is drawn, so a paid report is never
        // shown and then taken away.
        viewModelScope.launch {
            Billing.refresh()
            Billing.loadOffering()
        }
        viewModelScope.launch {
            Billing.unlocked.collect { unlocked -> _ui.update { it.copy(unlocked = unlocked) } }
        }
        viewModelScope.launch {
            Billing.offering.collect { offering ->
                _ui.update { it.copy(offer = offering?.availablePackages?.firstOrNull()) }
            }
        }
    }

    fun seed(text: String) {
        if (text.isBlank() || _ui.value.text.isNotEmpty()) return
        _ui.update { it.copy(text = text) }
    }

    fun edit(text: String) {
        _ui.update { it.copy(text = text, state = ScanState.Editing) }
    }

    fun loadSample() {
        val text = getApplication<Application>().assets
            .open("sample_lease.txt")
            .bufferedReader()
            .use { it.readText() }

        _ui.update { it.copy(text = text, state = ScanState.Editing) }
    }

    fun scan() {
        val clauses = ClauseSplitter.split(_ui.value.text)
        val findings = Scanner.scan(clauses)
            .sortedWith(compareBy({ it.severity.ordinal }, { it.clause.index }))

        _ui.update { it.copy(state = ScanState.Scanned(clauses.size, findings)) }
    }

    fun back() {
        _ui.update { it.copy(state = ScanState.Editing) }
    }

    fun buy(activity: Activity) {
        val pkg = _ui.value.offer ?: run {
            _ui.update { it.copy(message = "No offering is available right now.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            val error = Billing.purchase(activity, pkg)
            _ui.update { it.copy(busy = false, message = error) }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            val error = Billing.restore()
            _ui.update { it.copy(busy = false, message = error) }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }
}
