package jp.swapcalendar

import jp.swapcalendar.data.PairSettings
import jp.swapcalendar.data.ClosedHolding
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
    fun `calendar starts on Sunday`() {
        assertEquals(0, sundayFirstLeadingDays(YearMonth.of(2026, 11)))
        assertEquals(1, sundayFirstLeadingDays(YearMonth.of(2026, 6)))
        assertEquals(6, sundayFirstLeadingDays(YearMonth.of(2026, 8)))
    }

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

    @Test
    fun `places pairs with holdings before configured priority`() {
        val eur = row.copy(pairId = "3", symbol = "EUR/JPY")
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(row, eur),
            pairSettings = PairSettings(
                order = listOf("USD/JPY", "EUR/JPY"),
                quantities = mapOf("EUR/JPY" to 10_000L),
            ),
        )

        assertEquals(listOf("EUR/JPY", "USD/JPY"), state.symbols)
    }

    @Test
    fun `sums configured holdings for calendar day`() {
        val eur = row.copy(pairId = "3", symbol = "EUR/JPY", buySwap = "100.5")
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(row, eur),
            pairSettings = PairSettings(
                quantities = mapOf("USD/JPY" to 20_000L, "EUR/JPY" to 10_000L),
            ),
        )

        assertEquals(1_901L, state.estimatedDailySwap(state.rows))
    }

    @Test
    fun `returns unpublished when any held pair has no swap value`() {
        val unpublished = row.copy(pairId = "3", symbol = "EUR/JPY", buySwap = null)
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(row, unpublished),
            pairSettings = PairSettings(
                quantities = mapOf("USD/JPY" to 20_000L, "EUR/JPY" to 10_000L),
            ),
        )

        assertEquals(null, state.estimatedDailySwap(state.rows))
    }

    @Test
    fun `sums all held swap amounts regardless of display filters`() {
        val secondDay = row.copy(tradeDate = "2026-09-02", buySwap = "100.5")
        val hidden = row.copy(pairId = "3", symbol = "EUR/JPY", buySwap = "500")
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(row, secondDay, hidden),
            pairSettings = PairSettings(
                hidden = setOf("EUR/JPY"),
                quantities = mapOf("USD/JPY" to 20_000L, "EUR/JPY" to 10_000L),
            ),
        )

        assertEquals(2_502L, state.estimatedMonthlySwap())
    }

    @Test
    fun `daily total includes all holdings when another pair is selected`() {
        val eur = row.copy(pairId = "3", symbol = "EUR/JPY", buySwap = "100.5")
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(row, eur),
            selectedPairs = setOf("USD/JPY"),
            pairSettings = PairSettings(
                quantities = mapOf("USD/JPY" to 20_000L, "EUR/JPY" to 10_000L),
            ),
        )

        assertEquals(listOf("USD/JPY"), state.visibleRows.map { it.symbol })
        assertEquals(listOf("USD/JPY", "EUR/JPY"), state.heldRows.map { it.symbol })
        assertEquals(1_901L, state.estimatedDailySwap(state.heldRows))
    }

    @Test
    fun `excludes unpublished days from monthly total`() {
        val unpublished = row.copy(tradeDate = "2026-09-02", buySwap = null)
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(row, unpublished),
            pairSettings = PairSettings(quantities = mapOf("USD/JPY" to 20_000L)),
        )

        assertEquals(1_801L, state.estimatedMonthlySwap())
    }

    @Test
    fun `leaves monthly total empty when all held days are unpublished`() {
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(row.copy(buySwap = null)),
            pairSettings = PairSettings(quantities = mapOf("USD/JPY" to 20_000L)),
        )

        assertEquals(null, state.estimatedMonthlySwap())
    }

    @Test
    fun `counts swaps only between holding and release dates inclusive`() {
        val days = (1..5).map { day -> row.copy(tradeDate = "2026-09-0$day") }
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = days,
            pairSettings = PairSettings(
                closedHoldings = listOf(ClosedHolding("USD/JPY", 20_000L, PositionSide.BUY, "2026-09-02", "2026-09-04")),
            ),
        )

        assertEquals(listOf("2026-09-02", "2026-09-03", "2026-09-04"), state.heldRows.map { it.tradeDate })
        assertEquals(null, state.estimatedSwap(days[0]))
        assertEquals(5_403L, state.estimatedMonthlySwap())
        assertEquals(null, state.estimatedSwap(days[4]))
    }

    @Test
    fun `preserves a released position when a new holding is opened`() {
        val first = row.copy(tradeDate = "2026-09-02")
        val later = row.copy(tradeDate = "2026-09-05", buySwap = "100")
        val state = CalendarUiState(
            month = YearMonth.of(2026, 9),
            rows = listOf(first, later),
            pairSettings = PairSettings(
                quantities = mapOf("USD/JPY" to 10_000L),
                heldOn = mapOf("USD/JPY" to "2026-09-05"),
                closedHoldings = listOf(ClosedHolding("USD/JPY", 20_000L, PositionSide.BUY, "2026-09-01", "2026-09-03")),
            ),
        )

        assertEquals(1_801L, state.estimatedSwap(first))
        assertEquals(100L, state.estimatedSwap(later))
        assertEquals(1_901L, state.estimatedMonthlySwap())
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
