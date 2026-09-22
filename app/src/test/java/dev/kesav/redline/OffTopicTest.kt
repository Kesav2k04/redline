package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to text that is not a lease.
 *
 * The frozen corpus proved 20/20 costly clauses caught and 10/10 benign ones left alone,
 * and that number was believed for longer than it deserved: the benign half was ten
 * benign *lease* clauses. Nothing in it asked what the scanner does with a recipe, which
 * is close to the first thing a stranger with the app open will try.
 *
 * It turned out to do something embarrassing. Every document below tripped at least one
 * rule, and the recipe was told that the landlord alone decides what to deduct. Several
 * of these rules match ordinary commercial English: "at its sole discretion" is in every
 * employment offer written, and "continued use constitutes acceptance" is in every
 * privacy policy.
 *
 * The rules were not narrowed, because they are right about leases and narrowing them to
 * dodge a recipe would cost real catches. The document is checked instead, once, and the
 * findings over a non-lease are shown free and under a warning rather than sold.
 */
class OffTopicTest {

    private val recipe = """
        Preheat the oven to 200 degrees. Combine the flour and butter until the
        mixture resembles breadcrumbs, then add ten percent of the milk and stir.
        Rest the dough for thirty days in the refrigerator if you are making the
        long-ferment version, otherwise ninety minutes is enough.
        The baker may at their sole discretion add more salt.
    """.trimIndent()

    private val privacyPolicy = """
        We retain your personal data for ninety days after account closure. You may
        request deletion at any time by writing to us, and we will respond within
        thirty days. We may revise this policy at any time without notice to you.
        Continued use of the service after a revision constitutes acceptance of the
        revised terms. Any notice sent to the address on file is deemed received two
        days after dispatch.
    """.trimIndent()

    private val employmentOffer = """
        Your salary will be revised annually by ten percent subject to performance.
        The notice period is ninety days on either side. On resignation you shall
        remain liable for the unexpired portion of the retention bonus. The company
        may at its sole discretion withhold the final settlement for sixty days.
    """.trimIndent()

    private val newsArticle = """
        The council voted on Tuesday to raise parking charges by ten percent from
        April. Residents have thirty days to object. A spokesperson said the increase
        reflects maintenance costs that have risen every year since 2019.
    """.trimIndent()

    private val offTopic = mapOf(
        "recipe" to recipe,
        "privacy policy" to privacyPolicy,
        "employment offer" to employmentOffer,
        "news article" to newsArticle,
    )

    @Test
    fun `none of these documents is taken for a lease`() {
        for ((name, text) in offTopic) {
            assertFalse(
                "$name passed the lease check with ${LeaseCheck.distinctTerms(text)} terms",
                LeaseCheck.looksLikeLease(text),
            )
        }
    }

    @Test
    fun `the rules really do fire on them, which is why the check exists`() {
        // If this ever goes quiet the check has stopped earning its place, and the
        // comment above about the recipe has become a story rather than a fact.
        //
        // The news article used to be on this list. It tripped the occupant rule because
        // "reside" matched inside "Residents", and word boundaries on the triggers fixed
        // that at the source. Three still fire, because "at its sole discretion" and
        // "continued use constitutes acceptance" are ordinary English and always will be.
        for ((name, text) in offTopic - "news article") {
            val findings = Scanner.scan(ClauseSplitter.split(text))
            assertTrue("$name no longer trips any rule", findings.isNotEmpty())
        }
        val news = Scanner.scan(ClauseSplitter.split(newsArticle)).map { it.ruleId }
        assertFalse("residents read as a second occupant again", "occupant-surcharge" in news)
    }

    @Test
    fun `a non-lease is never charged for`() {
        for ((name, text) in offTopic) {
            val state = ScanState.Scanned(
                clauseCount = 1,
                findings = Scanner.scan(ClauseSplitter.split(text)),
                looksLikeLease = LeaseCheck.looksLikeLease(text),
            )
            assertFalse("$name would have reached the paywall", state.looksLikeLease)
        }
    }

    @Test
    fun `the real lease still passes`() {
        val lease = File("src/main/assets/sample_lease.txt").readText()
        assertTrue("the bundled sample stopped reading as a lease", LeaseCheck.looksLikeLease(lease))
    }

    @Test
    fun `one shared clause is enough`() {
        // Somebody sharing in a single paragraph of their lease sends one sentence, and
        // rejecting that would break the share-in path this app is built around.
        val single = "The Tenant shall pay a late fee of ten percent of the monthly rent."
        assertTrue("a single real clause was rejected", LeaseCheck.looksLikeLease(single))
    }

    @Test
    fun `one lease word on its own is not enough`() {
        // "Rent" alone is a car hire agreement, a tuxedo, a film.
        assertFalse(
            "a single lease-ish word let a document through",
            LeaseCheck.looksLikeLease("You may rent the vehicle for up to thirty days."),
        )
    }
}
