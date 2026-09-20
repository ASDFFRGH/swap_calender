package jp.swapcalendar.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OfficialRateSourceTest {
    @Test
    fun `maps official pair codes to bid and ask rates`() {
        val page = """<table>
            <tr class="commoditie-parent" data-code="999"><td>表示用の補助行</td></tr>
            <tr class="commoditie-parent" data-code="2"><td><span class="fxPair">USD/JPY</span></td></tr>
            <tr class="commoditie-parent" data-code="1"><td><span class="fxPair">EUR/USD</span></td></tr>
        </table>"""
        val json = """{"time":20260920195452654,"rate":{"2":["",0,0,0,0,156.867,156.871],"1":["",0,0,0,0,1.14859,1.14868]}}"""

        val result = parseOfficialRates(page, json)

        assertEquals("20260920195452654", result.fetchedAt)
        assertEquals(listOf("USD/JPY", "EUR/USD"), result.rates.map { it.symbol })
        assertEquals(BigDecimal("156.869"), result.rates.first().midpoint)
    }
}
