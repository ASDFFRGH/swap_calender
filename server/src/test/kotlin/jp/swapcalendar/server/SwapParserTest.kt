package jp.swapcalendar.server

import jp.swapcalendar.api.PublicationStateDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SwapParserTest {
    private val parser = SwapParser()

    @Test
    fun `parses zero and unpublished values without conflating them`() {
        val result = parser.parse(fixture("2026年9月"), YearMonth.of(2026, 9))

        assertEquals(4, result.points.size)
        val zero = result.points.single { it.sourcePairId == 2 && it.tradeDate == LocalDate.of(2026, 9, 1) }
        assertEquals(BigDecimal("0.0"), zero.buySwap)
        assertEquals(PublicationStateDto.PUBLISHED, zero.state)
        val unpublished = result.points.single { it.sourcePairId == 3 && it.tradeDate == LocalDate.of(2026, 9, 2) }
        assertEquals(null, unpublished.buySwap)
        assertEquals(null, unpublished.sellSwap)
        assertEquals(PublicationStateDto.UNPUBLISHED, unpublished.state)
    }

    @Test
    fun `rejects source fallback to a different month`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parser.parse(fixture("2026年8月"), YearMonth.of(2026, 9))
        }
        assertEquals("MONTH_MISMATCH", error.message)
    }

    private fun fixture(heading: String) = """
        <div class="month_nav"><p class="back">$heading</p></div>
        <select id="currencyPair_select">
          <option value="2">USD/JPY</option><option value="3">EUR/JPY</option>
        </select>
        <div class="tbl_list pcOnly"><table id="swaplist1">
          <thead><tr><th>取引日</th>
            <th colspan="3" class="th_commodity_2">USD/JPY</th>
            <th colspan="3" class="th_commodity_3">EUR/JPY</th>
          </tr></thead>
          <tbody>
            <tr><td>9月1日(火)</td><td>1</td><td>0.0</td><td>0.0</td><td>1</td><td>10.0</td><td>-10.0</td></tr>
            <tr><td>9月2日(水)</td><td>3</td><td>120.0</td><td>-120.0</td><td>3</td><td></td><td></td></tr>
          </tbody>
        </table></div>
    """.trimIndent()
}

