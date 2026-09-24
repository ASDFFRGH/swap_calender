package jp.swapcalendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.swapcalendar.data.FxRate
import jp.swapcalendar.data.OfficialRateSource
import jp.swapcalendar.data.PositionSide
import jp.swapcalendar.data.LeverageSettings
import jp.swapcalendar.data.LeverageSettingsStore
import jp.swapcalendar.data.PairSettings
import jp.swapcalendar.data.PairSettingsStore
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
    val deposit: String = "",
    val unrealizedLoss: String = "",
    val targetLeverage: String = "",
    val side: PositionSide = PositionSide.BUY,
    val pairSettings: PairSettings = PairSettings(),
    val unrealizedPnls: Map<String, String> = emptyMap(),
)

data class PositionValuation(
    val symbol: String,
    val quantity: Long,
    val exposure: BigDecimal,
    val unrealizedPnl: BigDecimal,
)

data class PortfolioCalculation(
    val effectiveEquity: BigDecimal,
    val totalExposure: BigDecimal,
    val currentLeverage: BigDecimal,
    val targetExposure: BigDecimal,
    val remainingExposure: BigDecimal,
    val additionalQuantity: Long,
)

internal fun calculatePortfolio(
    deposit: BigDecimal,
    positions: List<PositionValuation>,
    legacyUnrealizedLoss: BigDecimal,
    targetLeverage: BigDecimal,
    additionalBaseJpyRate: BigDecimal,
): PortfolioCalculation? {
    val equity = deposit.add(positions.sumOf { it.unrealizedPnl }).subtract(legacyUnrealizedLoss)
    if (equity <= BigDecimal.ZERO || targetLeverage <= BigDecimal.ZERO || additionalBaseJpyRate <= BigDecimal.ZERO) return null
    val exposure = positions.sumOf { it.exposure }
    val targetExposure = equity.multiply(targetLeverage)
    val remaining = targetExposure.subtract(exposure).max(BigDecimal.ZERO)
    return PortfolioCalculation(
        effectiveEquity = equity,
        totalExposure = exposure,
        currentLeverage = exposure.divide(equity, 3, RoundingMode.HALF_UP),
        targetExposure = targetExposure,
        remainingExposure = remaining,
        additionalQuantity = remaining.divide(additionalBaseJpyRate, 0, RoundingMode.FLOOR).longValueExact(),
    )
}

internal fun estimatePortfolioLossCut(
    calculation: PortfolioCalculation,
    selectedQuantity: Long,
    currentPairRate: BigDecimal,
    quoteJpyRate: BigDecimal,
    side: PositionSide,
): LossCutEstimate? {
    if (selectedQuantity <= 0 || currentPairRate <= BigDecimal.ZERO || quoteJpyRate <= BigDecimal.ZERO) return null
    val maintenanceMargin = calculation.totalExposure.multiply(BigDecimal("0.04"))
    val allowance = calculation.effectiveEquity.subtract(maintenanceMargin.multiply(BigDecimal("0.5")))
    if (allowance <= BigDecimal.ZERO) return null
    val distance = allowance.divide(BigDecimal.valueOf(selectedQuantity).multiply(quoteJpyRate), 8, RoundingMode.HALF_UP)
    val trigger = if (side == PositionSide.BUY) currentPairRate.subtract(distance).max(BigDecimal.ZERO) else currentPairRate.add(distance)
    return LossCutEstimate(trigger, distance, allowance, maintenanceMargin)
}

data class LeverageCalculation(
    val effectiveEquity: BigDecimal,
    val baseJpyRate: BigDecimal,
    val quantity: Long,
    val actualLeverage: BigDecimal,
)

data class LossCutEstimate(
    val triggerRate: BigDecimal,
    val rateDistance: BigDecimal,
    val lossAllowance: BigDecimal,
    val maintenanceMargin: BigDecimal,
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

internal fun estimateLossCut(
    calculation: LeverageCalculation,
    currentPairRate: BigDecimal,
    quoteJpyRate: BigDecimal,
    side: PositionSide,
): LossCutEstimate? {
    if (calculation.quantity <= 0 || currentPairRate <= BigDecimal.ZERO || quoteJpyRate <= BigDecimal.ZERO) return null
    val maintenanceMargin = calculation.baseJpyRate
        .multiply(BigDecimal.valueOf(calculation.quantity))
        .multiply(BigDecimal("0.04"))
    val lossCutEquity = maintenanceMargin.multiply(BigDecimal("0.5"))
    val allowance = calculation.effectiveEquity.subtract(lossCutEquity)
    if (allowance <= BigDecimal.ZERO) return null
    val distance = allowance.divide(
        BigDecimal.valueOf(calculation.quantity).multiply(quoteJpyRate),
        8,
        RoundingMode.HALF_UP,
    )
    val trigger = when (side) {
        PositionSide.BUY -> currentPairRate.subtract(distance).max(BigDecimal.ZERO)
        PositionSide.SELL -> currentPairRate.add(distance)
    }
    return LossCutEstimate(trigger, distance, allowance, maintenanceMargin)
}

@HiltViewModel
class LeverageViewModel @Inject constructor(
    private val rateSource: OfficialRateSource,
    private val settingsStore: LeverageSettingsStore,
    private val pairSettingsStore: PairSettingsStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LeverageUiState())
    val state: StateFlow<LeverageUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                mutableState.value = mutableState.value.copy(
                    selectedSymbol = settings.symbol ?: mutableState.value.selectedSymbol,
                    deposit = settings.deposit,
                    unrealizedLoss = settings.unrealizedLoss,
                    targetLeverage = settings.targetLeverage,
                    side = settings.side,
                    unrealizedPnls = settings.unrealizedPnls,
                )
            }
        }
        viewModelScope.launch {
            pairSettingsStore.settings.collect { settings ->
                mutableState.value = mutableState.value.copy(pairSettings = settings)
            }
        }
        refresh()
    }

    fun select(symbol: String) {
        mutableState.value = mutableState.value.copy(selectedSymbol = symbol)
        persist()
    }

    fun setDeposit(value: String) { mutableState.value = mutableState.value.copy(deposit = value); persist() }
    fun setUnrealizedLoss(value: String) { mutableState.value = mutableState.value.copy(unrealizedLoss = value); persist() }
    fun setTargetLeverage(value: String) { mutableState.value = mutableState.value.copy(targetLeverage = value); persist() }
    fun setSide(value: PositionSide) { mutableState.value = mutableState.value.copy(side = value); persist() }
    fun setPositionQuantity(symbol: String, value: String) {
        val quantity = value.filter(Char::isDigit).toLongOrNull() ?: 0L
        viewModelScope.launch { pairSettingsStore.setQuantity(symbol, quantity) }
    }
    fun setPositionSide(symbol: String, value: PositionSide) {
        viewModelScope.launch { pairSettingsStore.setSide(symbol, value) }
    }
    fun setUnrealizedPnl(symbol: String, value: String) {
        mutableState.value = mutableState.value.copy(
            unrealizedPnls = mutableState.value.unrealizedPnls.toMutableMap().apply {
                if (value.isBlank()) remove(symbol) else put(symbol, value)
            },
        )
        persist()
    }

    private fun persist() {
        val current = mutableState.value
        viewModelScope.launch {
            settingsStore.update(
                LeverageSettings(
                    symbol = current.selectedSymbol,
                    deposit = current.deposit,
                    unrealizedLoss = current.unrealizedLoss,
                    targetLeverage = current.targetLeverage,
                    side = current.side,
                    unrealizedPnls = current.unrealizedPnls,
                ),
            )
        }
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
