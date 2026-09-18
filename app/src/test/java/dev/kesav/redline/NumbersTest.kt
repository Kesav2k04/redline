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
}
