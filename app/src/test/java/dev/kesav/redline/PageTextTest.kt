package dev.kesav.redline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PageTextTest {

    @Test
    fun `a block that ends a sentence ends the paragraph`() {
        val text = PageText.assemble(
            listOf(listOf("1. The rent is due on the 5th.", "2. The deposit is ten months rent."))
        )
        assertEquals("1. The rent is due on the 5th.\n\n2. The deposit is ten months rent.", text)
    }

    @Test
    fun `a sentence cut by the bottom of a page is one clause again`() {
        val pages = listOf(
            listOf("7. If the Tenant leaves before eleven months the Tenant shall"),
            listOf("forfeit the entire security deposit."),
        )
        val clauses = ClauseSplitter.split(PageText.assemble(pages))

        assertEquals(1, clauses.size)
        assertTrue(clauses[0].text.contains("shall forfeit the entire security deposit"))
    }

    @Test
    fun `page numbers and running heads are dropped`() {
        val text = PageText.assemble(
            listOf(
                listOf("3. A late fee of 10 percent applies.", "Page 1 of 4"),
                listOf("2", "- 3 -", "4. The Tenant shall pay for repairs."),
            )
        )
        assertFalse(text.contains("Page 1"))
        assertFalse(text.lines().any { it.trim() == "2" || it.trim() == "- 3 -" })
        assertEquals(2, ClauseSplitter.split(text).size)
    }

    @Test
    fun `a clause that merely contains a number is not mistaken for a page number`() {
        val text = PageText.assemble(listOf(listOf("The notice period is 2 months.")))
        assertEquals("The notice period is 2 months.", text)
    }

    @Test
    fun `a list introduced by a colon stays with its lead-in`() {
        val text = PageText.assemble(
            listOf(listOf("The Landlord may deduct the following:", "(a) painting charges of Rs 5,000."))
        )
        assertEquals("The Landlord may deduct the following:\n\n(a) painting charges of Rs 5,000.", text)
    }

    @Test
    fun `blank and whitespace-only pieces add nothing`() {
        assertEquals("", PageText.assemble(listOf(listOf("   ", ""), emptyList())))
    }

    @Test
    fun `a findable clause survives the whole path`() {
        // What text recognition returns for a photographed clause: short wrapped lines,
        // one block, a hyphen at a line break.
        val block = "12. A late payment penalty of 10\npercent of the monthly rent will be\ncharged for each week of delay in pay-\nment."
        val clauses = ClauseSplitter.split(PageText.assemble(listOf(listOf(block))))
        val findings = Scanner.scan(clauses)

        assertEquals(1, clauses.size)
        assertTrue(clauses[0].text.contains("payment."))
        assertTrue(findings.isNotEmpty())
    }

    @Test
    fun `wrapped lines come back as paragraphs`() {
        val page = "RESIDENTIAL LEASE AGREEMENT\nThis agreement is made between the Landlord\nand the Tenant.\n" +
            "1. Term. The term shall be eleven\nmonths.\n2. Rent. The rent shall be paid on the\nfifth day."
        assertEquals(
            "RESIDENTIAL LEASE AGREEMENT\n\nThis agreement is made between the Landlord and the Tenant.\n\n" +
                "1. Term. The term shall be eleven months.\n\n2. Rent. The rent shall be paid on the fifth day.",
            PageText.assemble(listOf(listOf(page))),
        )
    }

    @Test
    fun `a wrapped line that starts with a number is not a new clause`() {
        val page = "4. The deposit shall be refunded within\n90 days of the Tenant vacating the premises."
        assertEquals(1, ClauseSplitter.split(PageText.assemble(listOf(listOf(page)))).size)
    }

    @Test
    fun `a PDF of the sample lease reads exactly like the pasted sample`() {
        // The same lease, typeset: every paragraph hard-wrapped near 70 characters, the
        // page break falling mid-clause, a page number on each page. The PDF path has to
        // find the same clauses and the same findings as pasting the text, or the count
        // on the results screen depends on how the lease arrived.
        val sample = File("src/main/assets/sample_lease.txt").readText().replace("\r\n", "\n")
        val wrapped = sample.split(Regex("\n{2,}")).joinToString("\n") { para ->
            para.trim().split(' ').fold(mutableListOf("")) { lines, word ->
                if (lines.last().length + word.length > 70) lines += word
                else lines[lines.lastIndex] = (lines.last() + " " + word).trim()
                lines
            }.joinToString("\n")
        }
        val lines = wrapped.lines()
        val half = lines.size / 2 + 3
        val pages = listOf(
            listOf(lines.take(half).joinToString("\n") + "\nPage 1 of 2"),
            listOf(lines.drop(half).joinToString("\n") + "\nPage 2 of 2"),
        )

        val pasted = ClauseSplitter.split(sample)
        val read = ClauseSplitter.split(PageText.assemble(pages))

        assertEquals(pasted.map { it.text }, read.map { it.text })
        assertEquals(
            Scanner.scan(pasted).map { it.ruleId to it.headline },
            Scanner.scan(read).map { it.ruleId to it.headline },
        )
    }
}
