package dev.kesav.redline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumbersTest {

    private val percent = Regex("percent")
    private val days = Regex("days?")
    private val months = Regex("months?")
    private val rupees = Regex("rupees")

    @Test
    fun `single word quantities`() {
        assertEquals(10, Numbers.valueBefore("a charge of ten percent of the rent", percent))
        assertEquals(90, Numbers.valueBefore("refunded within ninety days of vacating", days))
        assertEquals(11, Numbers.valueBefore("a term of eleven months", months))
    }

    @Test
    fun `scaled quantities`() {
        assertEquals(2000, Numbers.valueBefore("a charge of two thousand rupees", rupees))
        assertEquals(5000, Numbers.valueBefore("any repair below five thousand rupees", rupees))
    }

    @Test
    fun `compound quantities`() {
        assertEquals(45, Numbers.parse(listOf("forty", "five")))
        assertEquals(150, Numbers.parse(listOf("one", "hundred", "and", "fifty")))
        assertEquals(25000, Numbers.parse(listOf("twenty", "five", "thousand")))
    }

    @Test
    fun `digits are read as written`() {
        assertEquals(18, Numbers.valueBefore("interest at 18 percent per annum", percent))
    }

    @Test
    fun `a unit with no quantity yields nothing`() {
        assertNull(Numbers.valueBefore("payable in monthly instalments over months", months))
        assertNull(Numbers.valueBefore("the rent shall be paid", percent))
    }

    @Test
    fun `ordinals are not quantities`() {
        assertNull(Numbers.valueBefore("on or before the fifth day of each month", days))
    }

    @Test
    fun `the first stated quantity wins`() {
        assertEquals(3, Numbers.valueBefore("unpaid for three days after the due date, ten days later", days))
    }

    @Test
    fun `scanning stops at a non number word`() {
        assertEquals(6, Numbers.valueBefore("notice of six months", months))
        assertNull(Numbers.valueBefore("rent for the unexpired portion of the period in months", months))
    }

    @Test
    fun `the percent sign and per cent are the unit`() {
        assertEquals(10, Numbers.valueBefore("a late fee of 10% of the rent", percent))
        assertEquals(10, Numbers.valueBefore("a late fee of 10 % of the rent", percent))
        assertEquals(10, Numbers.valueBefore("a late fee of ten per cent of the rent", percent))
    }

    @Test
    fun `a decimal is one number, rounded down, and shown as written`() {
        val q = Numbers.quantities("interest at 12.5% per annum", percent).single()
        assertEquals(12, q.value)
        assertEquals("12.5", q.shown)
        // It used to read 1 + 5.
        assertEquals(1, Numbers.valueBefore("a charge of 1.5 percent a month", percent))
    }

    @Test
    fun `thousands separators do not split a number`() {
        assertEquals(5000, Numbers.valueBefore("any repair below 5,000 rupees", rupees))
        assertEquals(100000, Numbers.valueBefore("a deposit of 1,00,000 rupees", rupees))
    }

    @Test
    fun `a number run into its unit is still read`() {
        assertEquals(30, Numbers.valueBefore("refunded within 30days of vacating", days))
    }

    @Test
    fun `a spelled number with its numeral in brackets is one number`() {
        // US leases write "thirty (30) days". The brackets are dropped by the tokenizer, and
        // the run used to read as thirty plus 30.
        val q = Numbers.quantities("returned within twenty-one (21) days after the Tenant vacates", days).single()
        assertEquals(21, q.value)
        assertEquals("21", q.shown)
        assertEquals(1, Numbers.valueBefore("a security deposit equal to one (1) month's rent", months))
        assertEquals(10, Numbers.valueBefore("a term of ten (10) months", months))
        assertEquals(30, Numbers.valueBefore("returned within thirty (30) days", days))
        assertEquals(10, Numbers.valueBefore("a late fee of ten (10) percent of the rent", percent))
    }

    @Test
    fun `the numeral may come first`() {
        assertEquals(30, Numbers.valueBefore("returned within 30 (thirty) days", days))
    }

    @Test
    fun `a number written once is read as before`() {
        assertEquals(10, Numbers.valueBefore("a term of 10 months", months))
        assertEquals(10, Numbers.valueBefore("a term of ten months", months))
    }

    @Test
    fun `a numeral beside a different spelled number is not merged`() {
        // A scale word after a numeral multiplies it. Only a spelled run equal to its numeral
        // is the same number written twice.
        assertEquals(300000, Numbers.valueBefore("a deposit of 3 lakhs rupees", rupees))
        assertEquals(300000, Numbers.parse(listOf("3", "lakhs")))
        // A clause number before a spelled count is not equal to it, so it is left as 1.0.1
        // read it.
        assertEquals(15, Numbers.valueBefore("5. Ten months rent shall be paid in advance", months))
    }
}
