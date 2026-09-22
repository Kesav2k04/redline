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
     *
     * Narrow cuts both ways, and the first seventeen cut too deep. `OnTopicTest` put 29
     * real clauses through them and 14 came back rejected, including an assured
     * shorthold tenancy clause naming its own Act. A rejected lease is not a harmless
     * miss: the reader is told this does not read like a lease, and the report that
     * should have been sold is given away instead.
     *
     * Two words that would close most of what is left are kept out on purpose.
     * "deposit" pairs with "rent" to describe every car hire ever written, and "flat"
     * also spells "flat fee".
     */
    private val TERMS = listOf(
        // The parties, the instrument and the thing let.
        "tenant", "tenancy", "landlord", "lessor", "lessee", "lease",
        "rent", "premises", "demised", "sublet", "sub-let", "subleas",
        "leasehold", "occupier", "vacate", "eviction", "tenantable",
        "dwelling", "habitab",

        // Licensor, licensee, licence and licensed are one stem and not four.
        //
        // An Indian leave and licence deed is the common urban tenancy, and none of
        // that vocabulary was here, so a clause naming both parties and the licence fee
        // scored zero. Four separate stems would have fixed it and broken something
        // worse: "the Licensor grants the Licensee a licence to use the Software" would
        // have scored three and been sold a tenancy report. As one stem it scores one,
        // and a leave and licence clause has to bring a second word of its own.
        "licen",

        // Names of instruments, which no other kind of document has a reason to use.
        // These rescue the clause that says what the document is and then says nothing
        // else: the Housing Act recital, the statutory continuation, the deposit
        // scheme, the eleven-month grant.
        "leave and licence", "leave and license",
        "shorthold", "periodic tenancy", "tenancy deposit",

        // Not "residential" on its own. Broadband and software are sold for
        // residential use, which would sit one stem away from "licen".
        "residential purposes",
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
