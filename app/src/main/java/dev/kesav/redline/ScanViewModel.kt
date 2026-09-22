package dev.kesav.redline

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Editing : ScanState

    data class Scanned(
        val clauseCount: Int,
        val findings: List<Finding>,
    ) : ScanState {
        // Computed once here rather than on every read. As getters these walked the
        // findings list twice per recomposition of the results header, which is the one
        // composable guaranteed to recompose while the list scrolls.
        val flaggedClauses: Int = findings.map { it.clause.index }.distinct().size

        /**
         * Clauses carrying at least one costly finding, not the number of such findings.
         *
         * One clause routinely trips several rules: a deposit of ten months rent that is
         * also returned only after ninety days is two findings on one sentence. Counting
         * findings here made the screen read "11 of 16 clauses will cost you money, 13 of
         * them are worth arguing about", and 13 of 11 is not a thing.
         */
        val highClauses: Int = findings
            .filter { it.severity == Severity.HIGH }
            .map { it.clause.index }
            .distinct()
            .size
    }
}

data class ScanUi(
    val text: String = "",
    val state: ScanState = ScanState.Editing,
    val unlocked: Boolean = false,
    /** False until the first entitlement read lands. Distinct from `unlocked == false`. */
    val entitlementsKnown: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val offer: Package? = null,
)

/**
 * Which package the button sells.
 *
 * Reading the first available package would work today and break silently later:
 * the list is in dashboard order, so reordering the offering, or a product failing to
 * resolve and dropping out, would slide a subscription into that slot. The app would
 * then offer a recurring charge to somebody reading one lease, and nothing in the code
 * would have changed.
 *
 * So the choice is made by type, and there is deliberately no fallback. Selling the
 * wrong thing is worse than selling nothing, and the caller already handles null.
 */
internal fun chooseOffer(types: List<PackageType>): Int? =
    types.indexOf(PackageType.LIFETIME).takeIf { it >= 0 }

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(ScanUi())
    val ui: StateFlow<ScanUi> = _ui.asStateFlow()

    init {
        // Reading the entitlement is a network call, so it lands after the first frame.
        // The screen waits on `entitlementsKnown` rather than assuming that a false
        // `unlocked` means the reader has not paid.
        viewModelScope.launch {
            Billing.refresh()
            Billing.loadOffering()
        }
        viewModelScope.launch {
            Billing.unlocked.collect { unlocked -> _ui.update { it.copy(unlocked = unlocked) } }
        }
        viewModelScope.launch {
            Billing.known.collect { known -> _ui.update { it.copy(entitlementsKnown = known) } }
        }
        viewModelScope.launch {
            Billing.offering.collect { offering ->
                val packages = offering?.availablePackages.orEmpty()
                val offer = chooseOffer(packages.map { it.packageType })?.let(packages::getOrNull)

                // A package built from a custom identifier reports CUSTOM whatever it
                // sells, so a mis-set dashboard shows up here as a button with no price
                // rather than as an error. Name the types so the cause is readable.
                if (offer == null && packages.isNotEmpty()) {
                    Log.w(
                        "Billing",
                        "No one-time package in the offering. Types: " +
                            packages.joinToString { it.packageType.name },
                    )
                }

                _ui.update { it.copy(offer = offer) }
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
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().assets
                    .open("sample_lease.txt")
                    .bufferedReader()
                    .use { it.readText() }
            }
            _ui.update { it.copy(text = text, state = ScanState.Editing) }
        }
    }

    /**
     * Splitting and matching happen off the main thread.
     *
     * The bundled sample is sixteen clauses and finishes in no time, which is exactly
     * why this was easy to get wrong: the regex work scales with the length of the
     * pasted text, and a real forty-page commercial lease is a different number. The
     * cost of being right here is one dispatcher.
     */
    fun scan() {
        val text = _ui.value.text
        viewModelScope.launch {
            val scanned = withContext(Dispatchers.Default) {
                val clauses = ClauseSplitter.split(text)
                // Severity first, then findings that name an actual figure. "Deposit
                // equal to ten months rent" is a harder fact to argue with than "there
                // is a lock-in period", and the top card is the one a reader sees
                // before deciding.
                val findings = Scanner.scan(clauses).sortedWith(
                    compareBy(
                        { it.severity.ordinal },
                        { if (it.headline.any(Char::isDigit)) 0 else 1 },
                        { it.clause.index },
                    )
                )
                ScanState.Scanned(clauses.size, findings)
            }
            _ui.update { it.copy(state = scanned) }
        }
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
