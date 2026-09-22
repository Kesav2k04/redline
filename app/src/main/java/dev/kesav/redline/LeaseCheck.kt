package dev.kesav.redline

/**
 * Whether the pasted text is a tenancy agreement at all.
 *
 * Every rule here matches a turn of phrase, and several of those phrases are ordinary
 * commercial English. "At its sole discretion" appears in employment offers, software
 * licences and the back of a parking ticket. "Continued use constitutes acceptance"
 * appears in every privacy policy written. Run over a recipe, the scanner cheerfully
 * reported that the landlord alone decides what to deduct.
 *
 * The frozen corpus never caught this because its benign half was benign *lease*
 * clauses. Nothing in it asked what happens to text that is not a lease, which is the
 * first thing a stranger with the app open will paste.
 *
 * The question is about the document, not about any one rule, so it is answered once
 * here rather than bolted onto twenty-seven patterns. A tenancy agreement that never
 * says tenant, landlord, lease, rent or premises does not exist.
 */
object LeaseCheck {

    /**
     * Word stems that a tenancy agreement uses and other documents do not.
     *
     * Deliberately narrow. Broad words like "term", "notice", "party" and "agreement"
     * are in every contract ever written and would let the employment offer through,
     * which is the exact case this exists to stop.
     */
    private val TERMS = listOf(
        "tenant", "tenancy", "landlord", "lessor", "lessee", "lease",
        "rent", "premises", "demised", "sublet", "sub-let", "leasehold",
        "licensee", "occupier", "vacate", "eviction", "tenantable",
    ).map { Regex("\\b${Regex.escape(it)}", RegexOption.IGNORE_CASE) }

    /**
     * Two distinct terms, not one.
     *
     * One is too loose: "rent" alone catches a car hire agreement and the word "current"
     * is not a false positive only because the match is anchored to a word boundary.
     * Three is too tight: somebody sharing in a single clause of their lease sends one
     * sentence, and "The Tenant shall pay a late fee of ten percent of the monthly rent"
     * has exactly two. Two is the number that admits the real short input and rejects
     * all four documents that exposed the problem.
     */
    private const val NEEDED = 2

    fun looksLikeLease(text: String): Boolean = distinctTerms(text) >= NEEDED

    /** Exposed so the test can report how close a rejected document came. */
    internal fun distinctTerms(text: String): Int = TERMS.count { it.containsMatchIn(text) }
}
