package jp.swapcalendar.data

import io.ktor.client.HttpClient
import jp.swapcalendar.api.PublicationStateDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.YearMonth

class OfficialSwapSourceTest {
    private val source = OfficialSwapSource(HttpClient())

    @Test
    fun `parses official table and preserves zero versus unpublished`() {
        val response = source.parse(fixture("2026年9月"), YearMonth.of(2026, 9), "2026-09-20T00:00:00Z")

        assertEquals("2026-09", response.month)
        assertEquals(2, response.pairs.size)
        assertEquals(4, response.pairs.sumOf { it.points.size })
        val zero = response.pairs.first().points.first()
        assertEquals("0.0", zero.buySwap)
        assertEquals(PublicationStateDto.PUBLISHED, zero.publicationState)
        val unpublished = response.pairs.last().points.last()
        assertEquals(null, unpublished.buySwap)
        assertEquals(PublicationStateDto.UNPUBLISHED, unpublished.publicationState)
    }

    @Test
    fun `rejects fallback response for a different month`() {
        val error = assertThrows(ApiException::class.java) {
            source.parse(fixture("2026年8月"), YearMonth.of(2026, 9), "2026-09-20T00:00:00Z")
        }
        assertEquals("MONTH_NOT_AVAILABLE", error.code)
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
