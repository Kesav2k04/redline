package dev.kesav.redline

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One clause and everything wrong with it.
 *
 * The screen used to show one card per finding, which meant a security-deposit paragraph
 * that trips four rules appeared four times, quoted in full each time. Readers counted
 * that as padding, and it broke the arithmetic on the paywall: the heading said "11 of 16
 * clauses" and the button offered "18 more findings", two numbers in different units that
 * cannot be reconciled by anyone doing the subtraction. Grouping makes the unit the same
 * everywhere, which is the only way the two numbers can agree.
 */
data class ClauseGroup(val clause: Clause, val findings: List<Finding>) {
    /** HIGH sorts before MEDIUM, so the worst finding sets the badge for the clause. */
    val worst: Severity = findings.minByOrNull { it.severity.ordinal }?.severity ?: Severity.MEDIUM
}

sealed interface ScanState {
    data object Editing : ScanState

    data class Scanned(
        val clauseCount: Int,
        val findings: List<Finding>,
        /**
         * False when the text does not read like a tenancy agreement.
         *
         * The findings are still shown, because refusing to show them would hide the
         * evidence for the claim, but they are shown free and under a warning. Taking
         * money for a scan of a recipe is not a thing this app should be able to do.
         */
        val looksLikeLease: Boolean = true,
    ) : ScanState {
        /**
         * The findings, one entry per clause, in the order the findings were sorted.
         *
         * `groupBy` keeps first-appearance order, so the clause carrying the worst
         * finding stays at the top and the list still reads worst-first.
         */
        val groups: List<ClauseGroup> = findings
            .groupBy { it.clause.index }
            .map { (_, fs) -> ClauseGroup(fs.first().clause, fs) }

        // Computed once here rather than on every read. As getters these walked the
        // findings list twice per recomposition of the results header, which is the one
        // composable guaranteed to recompose while the list scrolls.
        val flaggedClauses: Int = groups.size

        /**
         * Whether there is anything behind the paywall to sell.
         *
         * The first clause is always shown free, so a report with one flagged clause has
         * nothing locked. The guard used to read `findings.size > 1` while the button read
         * `groups.size - 1`, and one clause routinely trips several rules, so a single
         * pasted clause produced "Show the other 0 clauses" over a report the reader could
         * already see in full. It lives here, next to the counts, so the paywall and the
         * label cannot disagree about the unit again.
         */
        val sellable: Boolean = looksLikeLease && groups.size > 1

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
    /** What the reader is waiting on while a PDF or photo is read, or null when idle. */
    val reading: String? = null,
    /** How much of a multi-page PDF has been read, 0 to 1, or null when there is no count. */
    val readingProgress: Float? = null,
    /** Where the text in the field came from, when it came from a file. */
    val source: String? = null,
    /**
     * Pages photographed into the field so far.
     *
     * A paper lease is several pages and a camera takes one, so each photo after the
     * first adds to the text rather than replacing it. Anything else that fills the
     * field resets this, so a photo taken after loading a PDF starts a new document
     * instead of being stapled to the end of an unrelated one.
     */
    val photoPages: Int = 0,
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

class ScanViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(
        ScanUi(
            text = saved[KEY_TEXT] ?: "",
            source = saved[KEY_SOURCE],
            photoPages = saved[KEY_PAGES] ?: 0,
        )
    )
    val ui: StateFlow<ScanUi> = _ui.asStateFlow()

    init {
        // Photographing a page hands the screen to the camera app, and a phone short of
        // memory kills the process behind it. Without this the reader comes back to an
        // empty field and the two pages they already photographed are gone. The scan
        // itself is not kept: it is recomputed from the text in well under a second.
        viewModelScope.launch {
            _ui.map { Triple(it.text, it.source, it.photoPages) }
                .distinctUntilChanged()
                .collect { (text, source, pages) ->
                    // Saved state crosses a Binder transaction, which fails outright
                    // somewhere past half a megabyte. A lease that long is not kept.
                    saved[KEY_TEXT] = text.takeIf { it.length <= MAX_SAVED_CHARS }
                    saved[KEY_SOURCE] = source
                    saved[KEY_PAGES] = pages
                }
        }
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

    /** A PDF or photo shared or opened from another app. Same rule as [seed]: once. */
    fun seedFile(uri: Uri?) {
        if (uri == null || _ui.value.text.isNotEmpty() || _ui.value.reading != null) return
        import(uri)
    }

    fun edit(text: String) {
        // Typing makes it the reader's text, so the note saying which file it came from
        // stops being true and goes.
        _ui.update { it.copy(text = text, state = ScanState.Editing, source = null, photoPages = 0) }
    }

    /**
     * Reads a PDF, a photo or a text file into the field.
     *
     * It fills the field rather than scanning straight away. Text recognition gets the
     * odd word wrong, and the reader should see what was read before being told what it
     * costs them; the field is where a misread "10" can be corrected.
     */
    fun import(uri: Uri, photo: Boolean = false, after: () -> Unit = {}) {
        if (_ui.value.reading != null) return after()
        viewModelScope.launch {
            _ui.update { it.copy(reading = "Opening the file", readingProgress = null, message = null) }
            val result = try {
                LeaseImport.read(getApplication(), uri) { step, done ->
                    _ui.update { it.copy(reading = step, readingProgress = done) }
                }
            } finally {
                after()
            }
            when (result) {
                is LeaseImport.Result.Failed ->
                    _ui.update { it.copy(reading = null, readingProgress = null, message = result.message) }

                is LeaseImport.Result.Read -> _ui.update { ui ->
                    val adding = photo && ui.photoPages > 0
                    val pages = if (photo) ui.photoPages + 1 else 0
                    ui.copy(
                        text = if (adding) ui.text.trimEnd() + "\n\n" + result.text else result.text,
                        state = ScanState.Editing,
                        reading = null,
                        readingProgress = null,
                        photoPages = pages,
                        source = describe(result, pages),
                    )
                }
            }
        }
    }

    private fun describe(read: LeaseImport.Result.Read, photoPages: Int): String {
        val what = when {
            photoPages > 0 -> if (photoPages == 1) "1 photographed page" else "$photoPages photographed pages"
            read.kind == LeaseImport.Kind.PDF && read.totalPages > read.pages ->
                "${read.name ?: "the PDF"}, first ${read.pages} of ${read.totalPages} pages"
            read.kind == LeaseImport.Kind.PDF ->
                "${read.name ?: "the PDF"}, ${read.pages} ${if (read.pages == 1) "page" else "pages"}"
            else -> read.name ?: "the file"
        }
        return "Read from $what, on this phone. Check the text, then scan."
    }

    fun loadSample() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().assets
                    .open("sample_lease.txt")
                    .bufferedReader()
                    .use { it.readText() }
            }
            _ui.update { it.copy(text = text, state = ScanState.Editing, source = null, photoPages = 0) }
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
                val findings = Scanner.ranked(clauses)
                ScanState.Scanned(clauses.size, findings, LeaseCheck.looksLikeLease(text))
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

    fun say(message: String) {
        _ui.update { it.copy(message = message) }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_SOURCE = "source"
        const val KEY_PAGES = "photoPages"
        const val MAX_SAVED_CHARS = 100_000
    }
}
