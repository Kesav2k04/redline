package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The other direction of the lease check.
 *
 * `OffTopicTest` asks what happens to a recipe. This asks what happens to a real lease
 * clause, which is the failure nobody goes looking for: when the check says no to a
 * genuine tenancy, the reader is told "this does not read like a lease", the findings
 * are given away, and the app has quietly refused to do the one job it exists for.
 *
 * Measured against the seventeen-stem list before any change here, 14 of the 29
 * clauses in this file were rejected: 9 of the 24 in the corpus below, and all five
 * of the known gaps. Every one of them is real drafting, and the rejections were not
 * exotic: an assured shorthold tenancy clause naming the Act, a deposit protection
 * clause, a licence fee clause, a dwelling-unit clause. The stems were written from a
 * British-and-American mental model of what a lease says, so the Indian leave and
 * licence vocabulary was absent almost entirely, and the word "Resident" that most
 * American apartment leases use for the tenant was missing too.
 *
 * Five clauses are still rejected and stay that way on purpose. They are in
 * `knownGaps`, each with the reason the stem that would fix it costs more than it buys.
 */
class OnTopicTest {

    /**
     * Indian leave and licence, the instrument almost every urban Indian tenancy uses.
     *
     * The parties are Licensor and Licensee, the money is a licence fee rather than
     * rent, and the subject is "the said flat" or "the licensed premises". Of the six
     * below, only three carried two of the original stems.
     */
    private val indian = mapOf(
        "licence fee" to
            "The Licensee shall pay to the Licensor a monthly licence fee of Rs. 45,000 " +
            "for the said premises on or before the fifth day of each month.",
        "residential use only" to
            "The said flat shall be used by the Licensee for residential purposes only " +
            "and for no other purpose whatsoever.",
        "eleven month term" to
            "This leave and licence is granted for a period of eleven months and may be " +
            "renewed thereafter by mutual consent of the parties.",
        "inspection right" to
            "The Licensor shall be entitled to enter upon the licensed premises for " +
            "inspection after giving twenty-four hours prior notice in writing.",
        "no parting with possession" to
            "The Licensee shall not sublet, assign or part with possession of the said " +
            "premises to any third party.",
        "outgoings" to
            "The Licensee shall bear and pay the electricity and water charges for the " +
            "licensed premises over and above the licence fee.",
    )

    /**
     * United States residential.
     *
     * Large apartment operators name the party "Resident" and the subject "the dwelling
     * unit", and neither word was in the list. A lease can run its whole length without
     * once saying tenant.
     */
    private val american = mapOf(
        "renters insurance" to
            "Tenant shall maintain renter's insurance throughout the Lease Term with " +
            "minimum liability coverage of \$100,000.",
        "condition of dwelling" to
            "Resident agrees to keep the dwelling unit in a clean, sanitary and " +
            "habitable condition at all times.",
        "deposit return window" to
            "The Security Deposit shall be returned within twenty-one days after Tenant " +
            "surrenders possession of the Premises.",
        "no pets" to
            "No pets of any kind are permitted on the Premises without the prior " +
            "written consent of the Landlord.",
        "no subleasing" to
            "Subleasing of the dwelling to any person not named in this Agreement is " +
            "prohibited and is grounds for termination.",
        "assignment by resident" to
            "Resident shall not assign this Agreement or sublease the dwelling unit " +
            "without the Owner's prior written approval.",
        "entry for repairs" to
            "Landlord may enter the unit upon twenty-four hours notice to make necessary " +
            "repairs or to show the unit to prospective renters.",
        "lead paint" to
            "Tenant acknowledges receipt of the lead-based paint disclosure for the " +
            "Premises, which was constructed prior to 1978.",
        "joint and several" to
            "Each Resident is jointly and severally liable for the full amount of rent " +
            "due under this Lease.",
    )

    /**
     * England and Wales, assured shorthold tenancy.
     *
     * The subject is "the Property" and the duration is "the Term", both too ordinary to
     * put in the list, so an AST clause often carries one usable word or none.
     */
    private val british = mapOf(
        "no animals" to
            "The Tenant shall not keep any animal or bird at the Property without the " +
            "Landlord's written consent.",
        "statutory basis" to
            "This Agreement creates an assured shorthold tenancy within the meaning of " +
            "the Housing Act 1988.",
        "deposit protection" to
            "The Deposit will be protected in a government-approved tenancy deposit " +
            "scheme within thirty days of receipt.",
        "yield up" to
            "The Tenant shall yield up the Property to the Landlord at the end of the " +
            "Term in the same condition, fair wear and tear excepted.",
        "quiet enjoyment" to
            "The Landlord covenants that the Tenant may quietly enjoy the Property " +
            "throughout the Term without interruption.",
        "rent in advance" to
            "The Tenant shall pay the rent monthly in advance on the first day of each " +
            "month by standing order.",
        "inventory" to
            "The Tenant shall permit the Landlord's agent to carry out an inventory " +
            "check at the beginning and end of the Term.",
        "periodic continuation" to
            "At the end of the fixed term the tenancy shall continue as a statutory " +
            "periodic tenancy until ended by either party.",
        "right to rent" to
            "The Tenant confirms that each occupier of the Property aged eighteen or " +
            "over has the right to rent in the United Kingdom.",
    )

    private val onTopic = indian + american + british

    /**
     * Real clauses the check still turns away, and the stem that would fix each one.
     *
     * Each is left rejected because the word that would rescue it is a word other
     * documents use, and letting it in would cost the thing `OffTopicTest` protects.
     * If one of these starts passing, the fix was worth making and it belongs above.
     */
    private val knownGaps = mapOf(
        // The only property-specific word here is "flat". A bare "flat" stem also
        // matches "flat fee" and "flat rate", so a software licence reading "the
        // Licensor grants the Licensee a licence for a flat annual fee" would reach two
        // stems and be sold a tenancy report.
        "goods left in the flat" to
            "The Licensor shall not be liable for any loss of or damage to the goods of " +
            "the Licensee lying in the said flat.",

        // "deposit" is the word that would carry this, and it is the one word that
        // cannot be added. Paired with "rent" it describes every car hire agreement
        // written, so the vehicle sentence OffTopicTest pins would pass the moment the
        // hire terms also mentioned a security deposit. The statutory phrase "tenancy
        // deposit" is in the list instead, which rescues the British clause and leaves
        // this one out.
        "security deposit on a flat" to
            "The interest-free refundable security deposit of Rs. 3,00,000 shall be " +
            "refunded to the Licensee at the time of handing over the said flat.",

        // "Rent" on its own is a car, a tuxedo and a film, which is the case
        // `OffTopicTest` pins with the vehicle sentence. One stem is one stem whoever
        // wrote it, and this clause genuinely cannot be told apart from a hire
        // agreement by vocabulary alone.
        "late fee on rent alone" to
            "A late fee of \$75 shall be assessed if Rent is not received by the fifth " +
            "day of the month.",

        // "The Let" is the whole signal. A "let" stem matches "letter", "letting" and
        // "let the dough rest", which is the recipe the check was built to stop.
        "the Let" to
            "The Let shall be for a fixed term of six months and shall continue " +
            "thereafter on a monthly periodic basis.",

        // "Landlord" is the only stem here and it is a decisive one: almost nothing
        // that is not a tenancy uses the word. The check cannot say so, because every
        // stem counts one and the threshold is two, so a decisive word and a weak word
        // like "deposit" carry identical weight. Fixing this properly means splitting
        // the list into stems that settle it alone and stems that need a partner, which
        // is a change to the rule rather than to the vocabulary.
        "repairing covenant" to
            "The Landlord shall keep in repair the structure and exterior of the " +
            "Property, including drains, gutters and external pipes.",
    )

    @Test
    fun `every genuine lease clause reads as a lease`() {
        val rejected = onTopic.filterValues { !LeaseCheck.looksLikeLease(it) }

        assertEquals(
            "rejected ${rejected.size} of ${onTopic.size}: " +
                rejected.keys.joinToString(", "),
            0,
            rejected.size,
        )
    }

    @Test
    fun `the known gaps are still exactly the five that are documented`() {
        val stillRejected = knownGaps
            .filterValues { !LeaseCheck.looksLikeLease(it) }
            .keys

        assertEquals(
            "a documented gap started passing; move it into the corpus above",
            knownGaps.keys,
            stillRejected,
        )
    }

    @Test
    fun `widening for the licence vocabulary did not admit a software licence`() {
        // The reason licensor, licensee, licence and licensed collapse into one stem
        // rather than four. As four they would have given a software agreement three
        // distinct terms and walked it straight through the check.
        val software = """
            The Licensor grants the Licensee a non-exclusive, non-transferable licence
            to use the Software for a flat annual fee. The Licensee shall not
            sublicense, redistribute or reverse engineer the Software. The Licensor may
            at its sole discretion suspend the licence on thirty days notice.
        """.trimIndent()

        assertFalse(
            "a software licence read as a tenancy with " +
                "${LeaseCheck.distinctTerms(software)} terms",
            LeaseCheck.looksLikeLease(software),
        )
    }

    @Test
    fun `the documents that must never pass still do not`() {
        // Restated here rather than left to OffTopicTest alone, because this file is
        // the one that widens the list and so this file is where a regression starts.
        val mustFail = listOf(
            "You may rent the vehicle for up to thirty days.",
            "The council voted on Tuesday to raise parking charges by ten percent from " +
                "April. Residents have thirty days to object.",
        )

        for (text in mustFail) {
            assertFalse(
                "widening let this through with ${LeaseCheck.distinctTerms(text)} terms",
                LeaseCheck.looksLikeLease(text),
            )
        }
    }

    @Test
    fun `the bundled sample is still a lease`() {
        val lease = File("src/main/assets/sample_lease.txt").readText()
        assertTrue(LeaseCheck.looksLikeLease(lease))
    }
}
