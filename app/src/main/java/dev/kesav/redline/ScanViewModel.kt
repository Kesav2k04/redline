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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
        /** Where the home is, as it was when this scan ran. */
        val place: Place? = null,
        /** Every clause, flagged or not, in document order, for the marked-up reading view. */
        val clauses: List<Clause> = emptyList(),
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
         * findings here made the screen read "11 of 16 clauses could cost you money, 13 of
         * them are worth arguing about", and 13 of 11 is not a thing.
         */
        val highClauses: Int = findings
            .filter { it.severity == Severity.HIGH }
            .map { it.clause.index }
            .distinct()
            .size

        /** The score, the four categories, the money and the void clauses, computed once. */
        val insight: Insight = Insights.of(findings, clauseCount, place, clauses)
    }
}

/** How long a forgotten lease can be brought back, before any accessibility allowance. */
internal const val UNDO_HOLD_MILLIS = 5_000L

data class ScanUi(
    val text: String = "",
    val state: ScanState = ScanState.Editing,
    /** Whether the report on screen is open: Pro, or a pass bought for this lease. */
    val unlocked: Boolean = false,
    /** Renter Pro: every lease on this phone, and the comparison. */
    val pro: Boolean = false,
    /** Fingerprints of the leases a pass was bought for. */
    val passes: Set<String> = emptySet(),
    /** What the paywall can sell, pass first, from the current offering. */
    val offers: List<Offer> = emptyList(),
    /** The monthly rent the reader typed, for leases that state rent only in months. */
    val rent: Long? = null,
    /** Leases scanned on this phone, newest first, for the comparison. */
    val saved: List<SavedLease> = emptyList(),
    /** A lease just forgotten, held so one tap can put it back, or null once the hold ends. */
    val forgotten: SavedLease? = null,
    /** False until the first entitlement read lands. Distinct from `unlocked == false`. */
    val entitlementsKnown: Boolean = false,
    /** True while the last entitlement read failed, so the screen knows to ask again. */
    val entitlementFailed: Boolean = false,
    /** Whether the last offering fetch reached the store. With no offers, "not on sale" rather than offline. */
    val storeReached: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    /**
     * What the last purchase or restore came to, in plain words. The paywall sheet draws above
     * the snackbar, so this shows in the sheet while it is open and in the snackbar otherwise.
     */
    val storeMessage: String? = null,
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
    /** Where the home is, if the reader has said. Null keeps the general wording. */
    val place: Place? = null,
    /**
     * The line above the price, from the offering's `paywall_line` metadata when the
     * dashboard sets one, so the pitch can change without shipping an APK. Null keeps the
     * line written in the app.
     */
    val pitch: String? = null,
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

internal const val PRO_NOT_ON = "Payment went through, but Renter Pro did not switch on. Tap Restore."

/**
 * What the sheet says once the store reports a purchase as made. A Pro plan that comes back
 * without the entitlement means the product is not attached to it on the dashboard: the reader
 * has paid and nothing opened, which must never pass in silence. A pass opens by the lease's
 * fingerprint rather than by the entitlement, so it has nothing to report here.
 */
internal fun afterPurchase(plan: Plan, entitled: Boolean): String? =
    if (plan != Plan.PASS && !entitled) PRO_NOT_ON else null

class ScanViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(
        ScanUi(
            text = saved[KEY_TEXT] ?: "",
            source = saved[KEY_SOURCE],
            photoPages = saved[KEY_PAGES] ?: 0,
            place = Place.fromName(prefs().getString(KEY_PLACE, null)),
            passes = prefs().getStringSet(KEY_PASSES, emptySet()).orEmpty().toSet(),
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
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { LeaseStore.all(getApplication()) }
            _ui.update { it.copy(saved = saved) }
        }
        // Reading the entitlement is a network call, so it lands after the first frame.
        // The screen waits on `entitlementsKnown` rather than assuming that a false
        // `unlocked` means the reader has not paid.
        viewModelScope.launch {
            Billing.refresh()
            Billing.loadOffering()
        }
        viewModelScope.launch {
            Billing.unlocked.collect { pro -> _ui.update { it.copy(pro = pro).withAccess() } }
        }
        viewModelScope.launch {
            Billing.known.collect { known -> _ui.update { it.copy(entitlementsKnown = known) } }
        }
        viewModelScope.launch {
            Billing.readFailed.collect { failed -> _ui.update { it.copy(entitlementFailed = failed) } }
        }
        viewModelScope.launch {
            Billing.storeReached.collect { reached -> _ui.update { it.copy(storeReached = reached) } }
        }
        viewModelScope.launch {
            Billing.offering.collect { offering ->
                val packages = offering?.availablePackages.orEmpty()
                val offer = offerFrom(offering)

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

                val pitch = offering?.getMetadataString(PITCH_KEY, "")?.takeIf { it.isNotBlank() }
                _ui.update { it.copy(offer = offer, pitch = pitch, offers = offersFrom(offering)) }
            }
        }
    }

    fun seed(text: String) {
        if (text.isBlank() || _ui.value.text.isNotEmpty()) return
        _ui.update { it.copy(text = text) }
    }

    /** A PDF or photo shared or opened from another app. Same rule as [seed]: once. */
    fun seedFile(uri: Uri?) {
        // Once per file, too, so a read the reader cancelled does not start again on rotation.
        if (uri == null || uri == seededFile || _ui.value.text.isNotEmpty() || _ui.value.reading != null) return
        seededFile = uri
        import(uri)
    }

    private var seededFile: Uri? = null

    fun edit(text: String) {
        // Typing makes it the reader's text, so the note saying which file it came from
        // stops being true and goes.
        _ui.update { it.copy(text = text, state = ScanState.Editing, source = null, photoPages = 0, rent = null) }
    }

    /**
     * Reads a PDF, a photo or a text file into the field.
     *
     * It fills the field rather than scanning straight away. Text recognition gets the
     * odd word wrong, and the reader should see what was read before being told what it
     * costs them; the field is where a misread "10" can be corrected.
     */
    fun import(uri: Uri, photo: Boolean = false, after: () -> Unit = {}) {
        // The scanner stays open for page after page, so a photo taken while the last one is
        // still being read waits its turn instead of being dropped. Anything else arriving
        // mid-read is refused, as before: it would replace the document being read.
        if (!photo && _ui.value.reading != null) return after()
        viewModelScope.launch(reads) { try { importLock.withLock {
            _ui.update { it.copy(reading = "Opening the file", readingProgress = null, message = null) }
            // A whole PDF read from its own text layer has nothing to proofread, and
            // parking the reader in front of fourteen pages of it before the verdict only
            // taught them to press Scan without looking. Recognised text still stops in the
            // editor, where a misread figure can be fixed first.
            var straightToScan = false
            val result = LeaseImport.read(getApplication(), uri) { step, done ->
                // A cancelled read can still report the page it was on, and that must not
                // bring the Reading screen back.
                _ui.update { if (isActive) it.copy(reading = step, readingProgress = done) else it }
            }
            when (result) {
                is LeaseImport.Result.Failed ->
                    _ui.update { it.copy(reading = null, readingProgress = null, message = result.message) }

                is LeaseImport.Result.Read -> _ui.update { ui ->
                    straightToScan = !photo && result.kind == LeaseImport.Kind.PDF &&
                        !result.recognised && result.pages == result.totalPages
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
            if (straightToScan) scan()
        } } finally {
            // Here rather than around the read, so a photo still waiting its turn when the
            // read is cancelled is deleted all the same.
            after()
        } }
    }

    private val importLock = kotlinx.coroutines.sync.Mutex()

    /** Parent of every read in flight or queued, so Cancel stops them all at once. */
    private var reads = SupervisorJob(viewModelScope.coroutineContext[Job])

    /**
     * Stops the PDF or photo being read and returns to where the reader was: the ways in, or
     * the pages already photographed. Nothing from the stopped read reaches the field.
     */
    fun cancelImport() {
        reads.cancel()
        reads = SupervisorJob(viewModelScope.coroutineContext[Job])
        _ui.update { it.copy(reading = null, readingProgress = null) }
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
        return if (read.recognised) {
            "Read from $what, on this phone. Check the text, then scan."
        } else {
            "Read from $what, on this phone."
        }
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
                val findings = Scanner.ranked(clauses, _ui.value.place)
                ScanState.Scanned(clauses.size, findings, LeaseCheck.looksLikeLease(text), _ui.value.place, clauses)
            }
            _ui.update { it.copy(state = scanned).withAccess() }
            // Every lease that reads as one is kept on the phone for comparing later.
            if (scanned.looksLikeLease && text.isNotBlank()) {
                // A lease scanned again keeps the name it was first saved under.
                val title = _ui.value.saved.firstOrNull { it.id == leaseFingerprint(text) }?.title
                    ?: LeaseStore.titleFor(text, _ui.value.source)
                val saved = storeLock.withLock {
                    withContext(Dispatchers.IO) { LeaseStore.save(getApplication(), text, title, _ui.value.place) }
                }
                _ui.update { it.copy(saved = saved) }
            }
        }
    }

    /** A lease scanned before on this phone, read again with today's rules. */
    fun openSaved(lease: SavedLease) {
        _ui.update { it.copy(text = lease.text, source = null, photoPages = 0, rent = null) }
        scan()
    }

    /**
     * Removes a saved lease and holds it in [ScanUi.forgotten] for [holdMillis], so one tap
     * can bring it back. The file is written at once: a process killed during the hold leaves
     * the lease forgotten, which is what the reader asked for. A second forget ends the first
     * one's hold, so only the latest can be undone.
     */
    fun forget(id: String, holdMillis: Long = UNDO_HOLD_MILLIS) {
        val lease = _ui.value.saved.firstOrNull { it.id == id }
        forgetHold?.cancel()
        forgetHold = viewModelScope.launch {
            val saved = storeLock.withLock { withContext(Dispatchers.IO) { LeaseStore.remove(getApplication(), id) } }
            _ui.update { it.copy(saved = saved, forgotten = lease) }
            if (lease != null) {
                delay(holdMillis)
                _ui.update { it.copy(forgotten = null) }
            }
        }
    }

    /** Writes back the lease [forget] is holding, under its own title and date. */
    fun undoForget() {
        val lease = _ui.value.forgotten ?: return
        forgetHold?.cancel()
        _ui.update { it.copy(forgotten = null) }
        viewModelScope.launch {
            val saved = storeLock.withLock { withContext(Dispatchers.IO) { LeaseStore.restore(getApplication(), lease) } }
            _ui.update { it.copy(saved = saved) }
        }
    }

    private var forgetHold: Job? = null

    // Saving, forgetting and restoring each read the whole file and write it back, so two at
    // once would lose one of them.
    private val storeLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The reader says where the home is. Kept on the phone, never sent anywhere, and the
     * open report is scanned again so its asks carry that place's figures at once.
     */
    fun choosePlace(place: Place?) {
        prefs().edit().putString(KEY_PLACE, place?.name).apply()
        _ui.update { it.copy(place = place) }
        if (_ui.value.state is ScanState.Scanned) scan()
    }

    private fun prefs() = getApplication<Application>()
        .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun back() {
        _ui.update { it.copy(state = ScanState.Editing) }
    }

    fun buy(activity: Activity, plan: Plan? = null) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null, storeMessage = null) }
            // The app says it works offline, and it does, which means it can start with
            // no offering at all. The offering was only ever fetched at launch, so a
            // reader who scanned on the metro and then got signal was told no offering
            // existed. Ask again at the moment it matters.
            if (_ui.value.offers.isEmpty()) Billing.loadOffering()
            val offers = _ui.value.offers.ifEmpty { offersFrom(Billing.offering.value) }
            val chosen = offers.firstOrNull { it.plan == plan }
                ?: offers.firstOrNull { it.plan == Plan.PRO_LIFETIME }
                ?: offers.firstOrNull()
            if (chosen == null) {
                _ui.update { it.copy(busy = false, storeMessage = Billing.STORE_UNREACHABLE) }
                return@launch
            }
            // The lease is fingerprinted before the purchase sheet goes up, so a pass opens
            // the lease that was on screen when the reader chose to pay for it.
            val lease = leaseFingerprint(_ui.value.text)
            when (val outcome = Billing.purchase(activity, chosen.pkg)) {
                Billing.Outcome.Bought -> {
                    if (chosen.plan == Plan.PASS) {
                        val passes = _ui.value.passes + lease
                        prefs().edit().putStringSet(KEY_PASSES, passes).apply()
                        _ui.update { it.copy(passes = passes) }
                    }
                    val note = afterPurchase(chosen.plan, Billing.unlocked.value)
                    if (note != null) {
                        Log.e("Billing", "Bought ${chosen.pkg.product.id}, but ${Billing.ENTITLEMENT} is not active.")
                    }
                    _ui.update { it.copy(busy = false, storeMessage = note).withAccess() }
                }
                Billing.Outcome.Cancelled -> _ui.update { it.copy(busy = false) }
                // Bought on another phone, which is another purchase ID here, so Play refuses to
                // sell it again. A restore brings it across instead of a dead end.
                Billing.Outcome.Owned -> {
                    val problem = Billing.restore()
                    _ui.update { it.copy(busy = false, storeMessage = problem?.let { Billing.ALREADY_OWNED }).withAccess() }
                }
                is Billing.Outcome.Failed -> _ui.update { it.copy(busy = false, storeMessage = outcome.message) }
            }
        }
    }

    /**
     * Fetches the offering again when the paywall is on screen without one, so the price
     * appears on the button once the phone is back online rather than never. Keyed on every
     * plan, not the lifetime one alone, which kept polling an offering that sold only monthly.
     */
    fun retryOffer() {
        if (_ui.value.offers.isNotEmpty() || offerRetry?.isActive == true) return
        offerRetry = viewModelScope.launch { Billing.loadOffering() }
    }

    private var offerRetry: kotlinx.coroutines.Job? = null

    /**
     * Reads the entitlement again when the last read failed, so a buyer who opened the app
     * offline gets the report back once the signal returns, without a restart.
     */
    fun retryEntitlement() {
        if (!Billing.readFailed.value || entitlementRetry?.isActive == true) return
        entitlementRetry = viewModelScope.launch { Billing.refresh() }
    }

    private var entitlementRetry: Job? = null

    /** Pro opens everything; a pass opens only the lease it was bought for. */
    private fun ScanUi.withAccess(): ScanUi =
        copy(unlocked = pro || (text.isNotBlank() && leaseFingerprint(text) in passes))

    private fun offerFrom(offering: com.revenuecat.purchases.Offering?): Package? {
        val packages = offering?.availablePackages.orEmpty()
        return chooseOffer(packages.map { it.packageType })?.let(packages::getOrNull)
    }

    fun restore() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null, storeMessage = null) }
            val error = Billing.restore()
            _ui.update { it.copy(busy = false, storeMessage = error) }
        }
    }

    /** Kept for this lease only: typing a new lease clears it. Never leaves the phone. */
    fun setRent(rent: Long?) {
        _ui.update { it.copy(rent = rent?.takeIf { r -> r > 0 }) }
    }

    fun say(message: String) {
        _ui.update { it.copy(message = message) }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun dismissStoreMessage() {
        _ui.update { it.copy(storeMessage = null) }
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val PREFS = "redline"
        const val PITCH_KEY = "paywall_line"
        const val KEY_PLACE = "place"
        const val KEY_SOURCE = "source"
        const val KEY_PAGES = "photoPages"
        const val KEY_PASSES = "passes"
        const val MAX_SAVED_CHARS = 100_000
    }
}
