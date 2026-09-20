package jp.swapcalendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.swapcalendar.data.FxRate
import jp.swapcalendar.data.OfficialRateSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class LeverageUiState(
    val rates: List<FxRate> = emptyList(),
    val selectedSymbol: String? = null,
    val fetchedAt: String? = null,
    val loading: Boolean = false,
    val message: String? = null,
)

data class LeverageCalculation(
    val effectiveEquity: BigDecimal,
    val baseJpyRate: BigDecimal,
    val quantity: Long,
    val actualLeverage: BigDecimal,
)

internal fun calculateQuantity(
    deposit: BigDecimal,
    unrealizedLoss: BigDecimal,
    leverage: BigDecimal,
    baseJpyRate: BigDecimal,
): LeverageCalculation? {
    val equity = deposit.subtract(unrealizedLoss)
    if (equity <= BigDecimal.ZERO || leverage <= BigDecimal.ZERO || baseJpyRate <= BigDecimal.ZERO) return null
    val quantity = equity.multiply(leverage).divide(baseJpyRate, 0, RoundingMode.FLOOR).longValueExact()
    val actual = baseJpyRate.multiply(BigDecimal.valueOf(quantity)).divide(equity, 3, RoundingMode.HALF_UP)
    return LeverageCalculation(equity, baseJpyRate, quantity, actual)
}

@HiltViewModel
class LeverageViewModel @Inject constructor(private val rateSource: OfficialRateSource) : ViewModel() {
    private val mutableState = MutableStateFlow(LeverageUiState())
    val state: StateFlow<LeverageUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun select(symbol: String) {
        mutableState.value = mutableState.value.copy(selectedSymbol = symbol)
    }

    fun refresh() {
        if (mutableState.value.loading) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, message = null)
            runCatching { rateSource.latest() }
                .onSuccess { snapshot ->
                    val current = mutableState.value.selectedSymbol
                    mutableState.value = mutableState.value.copy(
                        rates = snapshot.rates,
                        selectedSymbol = current?.takeIf { symbol -> snapshot.rates.any { it.symbol == symbol } }
                            ?: snapshot.rates.firstOrNull()?.symbol,
                        fetchedAt = snapshot.fetchedAt,
                        loading = false,
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        message = error.message ?: "為替レートの取得に失敗しました",
                    )
                }
        }
    }
}
