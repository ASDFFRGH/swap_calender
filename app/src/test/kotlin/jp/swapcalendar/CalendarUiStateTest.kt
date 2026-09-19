package jp.swapcalendar

import jp.swapcalendar.data.PairSettings
import jp.swapcalendar.data.PositionSide
import jp.swapcalendar.data.SwapPointRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.YearMonth

class CalendarUiStateTest {
    private val row = SwapPointRow(
        pairId = "2",
        symbol = "USD/JPY",
        tradeDate = "2026-09-01",
        spDays = 3,
        buySwap = "900.9",
        sellSwap = "-930.9",
        publicationState = "PUBLISHED",
    )

    @Test
    fun `calculates buy receipt for actual quantity and truncates fractions`() {
        val state = state(PositionSide.BUY)
        assertEquals(1_801L, state.estimatedSwap(row))
    }

    @Test
    fun `calculates sell payment and rounds away from zero`() {
        val state = state(PositionSide.SELL)
        assertEquals(-1_862L, state.estimatedSwap(row))
    }

    @Test
    fun `applies configured priority and hides disabled pairs`() {
        val eur = row.copy(pairId = "3", symbol = "EUR/JPY")
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(row, eur),
            pairSettings = PairSettings(
                order = listOf("EUR/JPY", "USD/JPY"),
                hidden = setOf("USD/JPY"),
            ),
        )

        assertEquals(listOf("EUR/JPY", "USD/JPY"), state.symbols)
        assertEquals(listOf("EUR/JPY"), state.visibleRows.map { it.symbol })
    }

    private fun state(side: PositionSide) = CalendarUiState(
        month = YearMonth.of(2026, 9),
        rows = listOf(row),
        pairSettings = PairSettings(
            quantities = mapOf("USD/JPY" to 20_000L),
            sides = mapOf("USD/JPY" to side),
        ),
    )
}
