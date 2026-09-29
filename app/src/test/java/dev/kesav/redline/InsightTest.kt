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
    fun anAnnualInterestRateIsNotALateFee() {
        // On a $2,000 rent this used to read "Late payment penalty of 12 percent" and put
        // "Late fee, $240, each late payment" on the money card.
        val clauses = listOf(
            Clause(0, "The monthly rent is \$2,000 per month."),
            Clause(1, "A late fee of \$50 plus interest at 12% per annum is payable on rent paid late."),
        )
        val findings = Scanner.ranked(clauses)
        assertTrue("late-fee fired on the interest rate", findings.none { it.ruleId == "late-fee" })
        assertTrue("the interest rate was lost", findings.any { it.ruleId == "interest-rate" })
        val exposure = Insights.of(findings, clauses.size, null, clauses).exposure
        assertTrue(exposure?.items.orEmpty().none { it.ruleId == "late-fee" })
    }

    @Test
    fun theLateFeeCardQuotesTheFigureInTheHeadline() {
        val clauses = listOf(
            Clause(0, "The monthly rent is \$2,000 per month."),
            Clause(1, "Rent increases by 10% each year; a late fee of 5% applies to rent paid late."),
        )
        val findings = Scanner.ranked(clauses)
        assertEquals("Late payment penalty of 5 percent", findings.single { it.ruleId == "late-fee" }.headline)
        val exposure = Insights.of(findings, clauses.size, null, clauses).exposure!!
        // 5% of $2,000, not the 10% rent rise that comes first in the clause.
        assertEquals(100L, exposure.amountOf(exposure.items.single { it.ruleId == "late-fee" }))
    }

    @Test
    fun aDepositSumIsTheOneBesideTheWordDeposit() {
        val clause = Clause(0, "The monthly rent is Rs. 1,00,000 and the security deposit of Rs. 50,000 shall be interest free.")
        val exposure = Insights.of(Scanner.ranked(listOf(clause)), 1, null, listOf(clause)).exposure!!
        assertEquals(50_000L, exposure.total())
    }

    @Test
    fun aDecimalMonthCountIsReadWhole() {
        // "1.5 months' rent" used to be read from the 5 after the point: $10,000 at stake, not $3,000.
        val clauses = listOf(
            Clause(0, "The monthly rent is \$2,000 per month."),
            Clause(1, "The Tenant shall pay a security deposit equal to 1.5 months' rent, which shall be refunded within ninety days of the Tenant vacating the premises."),
        )
        val exposure = Insights.of(Scanner.ranked(clauses), clauses.size, null, clauses).exposure!!
        assertEquals(1.5, exposure.months, 0.001)
        assertEquals(3_000L, exposure.total())
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
