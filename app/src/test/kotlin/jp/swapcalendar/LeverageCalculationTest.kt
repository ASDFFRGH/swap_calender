package jp.swapcalendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import jp.swapcalendar.data.PositionSide
import java.math.BigDecimal

class LeverageCalculationTest {
    @Test
    fun `calculates portfolio leverage and additional quantity from multiple positions`() {
        val result = calculatePortfolio(
            deposit = BigDecimal("1000000"),
            positions = listOf(
                PositionValuation("USD/JPY", 10_000, BigDecimal("1500000"), BigDecimal("-50000")),
                PositionValuation("EUR/JPY", 5_000, BigDecimal("800000"), BigDecimal("20000")),
            ),
            legacyUnrealizedLoss = BigDecimal.ZERO,
            targetLeverage = BigDecimal("3"),
            additionalBaseJpyRate = BigDecimal("150"),
        )

        assertEquals(BigDecimal("970000"), result?.effectiveEquity)
        assertEquals(BigDecimal("2300000"), result?.totalExposure)
        assertEquals(BigDecimal("2.371"), result?.currentLeverage)
        assertEquals(4_066L, result?.additionalQuantity)
    }

    @Test
    fun `additional quantity is zero when portfolio already exceeds target leverage`() {
        val result = calculatePortfolio(
            BigDecimal("100000"),
            listOf(PositionValuation("USD/JPY", 10_000, BigDecimal("1500000"), BigDecimal.ZERO)),
            BigDecimal.ZERO,
            BigDecimal("10"),
            BigDecimal("150"),
        )

        assertEquals(BigDecimal.ZERO, result?.remainingExposure)
        assertEquals(0L, result?.additionalQuantity)
    }

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
