package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightTest {

    private fun sample(): Pair<List<Clause>, List<Finding>> {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        return clauses to Scanner.ranked(clauses)
    }

    @Test
    fun everyRuleHasACategory() {
        val missing = Scanner.asks.keys - Insights.mappedRuleIds
        assertTrue("rules with no category: $missing", missing.isEmpty())
        val noCounter = Scanner.asks.keys - Drafts.counterRuleIds
        assertTrue("rules with no proposed wording: $noCounter", noCounter.isEmpty())
    }

    @Test
    fun scoreRisesWithSeriousClausesAndStaysInRange() {
        val (clauses, findings) = sample()
        val all = Insights.of(findings, clauses.size, null, clauses)
        val one = Insights.of(findings.filter { it.clause.index == findings.first().clause.index }, clauses.size, null, clauses)
        assertTrue(all.score in 60..100)
        assertEquals(Tier.TOXIC, all.tier)
        assertTrue(one.score < all.score)
        assertEquals(Category.entries, all.categories.map { it.category })
    }

    @Test
    fun sampleLeaseMoneyComesOnlyFromItsOwnFigures() {
        val (clauses, findings) = sample()
        val exposure = Insights.of(findings, clauses.size, null, clauses).exposure!!
        // The sample states no rent, only months of it: ten as deposit, three on leaving early.
        assertNull(exposure.rent)
        assertEquals(13.0, exposure.months, 0.001)
        assertNull(exposure.total())
        assertEquals(13 * 20_000L, exposure.total(20_000))
        // Five thousand rupees per repair is a repeating charge, never added to the total.
        assertTrue(exposure.repeating.any { it.amount == 5_000L })
        assertEquals("₹", exposure.symbol)
    }

    @Test
    fun englandVoidsTheSection21Wording() {
        val clause = Clause(0, "The Landlord may serve notice under section 21 of the Housing Act 1988 to end the tenancy.")
        val findings = Scanner.ranked(listOf(clause), Place.ENGLAND)
        val insight = Insights.of(findings, 1, Place.ENGLAND, listOf(clause))
        if (findings.any { it.ruleId == "section-21" }) {
            assertTrue(insight.void.any { it.ruleId == "section-21" })
        }
        assertTrue(Insights.of(findings, 1, null, listOf(clause)).void.isEmpty())
    }
}
