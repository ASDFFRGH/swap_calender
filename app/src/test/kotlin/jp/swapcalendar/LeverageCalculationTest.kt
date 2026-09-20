package jp.swapcalendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
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
}
