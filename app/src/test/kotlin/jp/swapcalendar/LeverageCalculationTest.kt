package jp.swapcalendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import jp.swapcalendar.data.PositionSide
import java.math.BigDecimal

class LeverageCalculationTest {
    @Test
    fun `calculates quantity using effective equity and base yen rate`() {
        val result = calculateQuantity(
            deposit = BigDecimal("1000000"),
            unrealizedLoss = BigDecimal("100000"),
            leverage = BigDecimal("2.5"),
            baseJpyRate = BigDecimal("150"),
        )

        assertEquals(15_000L, result?.quantity)
        assertEquals(BigDecimal("2.500"), result?.actualLeverage)
    }

    @Test
    fun `rejects calculation when loss consumes deposit`() {
        assertNull(calculateQuantity(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE))
    }

    @Test
    fun `estimates buy loss cut rate at fifty percent maintenance margin`() {
        val calculation = calculateQuantity(
            BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal("10"), BigDecimal("150"),
        )!!

        val result = estimateLossCut(calculation, BigDecimal("150"), BigDecimal.ONE, PositionSide.BUY)

        assertEquals(BigDecimal("137.99985000"), result?.triggerRate)
        assertEquals(BigDecimal("800002.000"), result?.lossAllowance)
    }

    @Test
    fun `uses quote currency yen rate for cross pair loss cut`() {
        val calculation = calculateQuantity(
            BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal("10"), BigDecimal("180"),
        )!!

        val result = estimateLossCut(
            calculation, BigDecimal("1.2"), BigDecimal("150"), PositionSide.BUY,
        )

        assertEquals(BigDecimal("1.10399880"), result?.triggerRate)
    }
}
