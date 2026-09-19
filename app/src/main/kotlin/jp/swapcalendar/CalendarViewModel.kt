package jp.swapcalendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.swapcalendar.data.SwapPointRow
import jp.swapcalendar.data.SwapRepository
import jp.swapcalendar.data.PairSettings
import jp.swapcalendar.data.PairSettingsStore
import jp.swapcalendar.data.PositionSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class CalendarUiState(
    val month: YearMonth,
    val rows: List<SwapPointRow> = emptyList(),
    val selectedDate: LocalDate? = null,
    val selectedPairs: Set<String> = emptySet(),
    val sourceFetchedAt: String? = null,
    val lastSyncedAt: String? = null,
    val refreshing: Boolean = false,
    val message: String? = null,
    val pairSettings: PairSettings = PairSettings(),
    val settingsOpen: Boolean = false,
) {
    val symbols: List<String> get() = pairSettings.orderedSymbols(rows.map { it.symbol }.distinct())
    val visibleRows: List<SwapPointRow> get() {
        val order = symbols.withIndex().associate { it.value to it.index }
        return rows.asSequence()
            .filterNot { it.symbol in pairSettings.hidden }
            .filter { selectedPairs.isEmpty() || it.symbol in selectedPairs }
            .sortedWith(compareBy<SwapPointRow> { it.tradeDate }.thenBy { order[it.symbol] ?: Int.MAX_VALUE })
            .toList()
    }
    val selectedRows: List<SwapPointRow> get() = visibleRows.filter { it.tradeDate == selectedDate?.toString() }

    fun quantity(symbol: String): Long = pairSettings.quantities[symbol] ?: 0L
    fun side(symbol: String): PositionSide = pairSettings.sides[symbol] ?: PositionSide.BUY
    fun estimatedSwap(row: SwapPointRow): Long? {
        val quantity = quantity(row.symbol)
        if (quantity <= 0) return null
        val value = when (side(row.symbol)) {
            PositionSide.BUY -> row.buySwap
            PositionSide.SELL -> row.sellSwap
        }?.toBigDecimalOrNull() ?: return null
        return value.multiply(BigDecimal.valueOf(quantity))
            .divide(BigDecimal("10000"))
            .setScale(0, RoundingMode.FLOOR)
            .longValueExact()
    }
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: SwapRepository,
    private val settingsStore: PairSettingsStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val initialMonth = savedStateHandle.get<String>("month")?.let(YearMonth::parse)
        ?: YearMonth.now(ZoneId.of("Asia/Tokyo"))
    private val month = MutableStateFlow(initialMonth)
    private val selectedDate = MutableStateFlow<LocalDate?>(initialMonth.atDay(1))
    private val selectedPairs = MutableStateFlow<Set<String>>(emptySet())
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val settingsOpen = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val cached = month.flatMapLatest { repository.observeMonth(it.toString()) }
    private val selection = combine(month, cached, selectedDate, selectedPairs, ::Selection)
    private val configuration = combine(selection, settingsStore.settings, settingsOpen, ::Configuration)
    val uiState: StateFlow<CalendarUiState> = combine(configuration, refreshing, message) { configuration, isRefreshing, currentMessage ->
        val selection = configuration.selection
        CalendarUiState(
            month = selection.month,
            rows = selection.cached.rows,
            selectedDate = selection.date,
            selectedPairs = selection.pairs,
            sourceFetchedAt = selection.cached.metadata?.sourceFetchedAt,
            lastSyncedAt = selection.cached.metadata?.lastSyncedAt,
            refreshing = isRefreshing,
            message = currentMessage,
            pairSettings = configuration.settings,
            settingsOpen = configuration.settingsOpen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState(initialMonth))

    init { refresh() }

    fun moveMonth(offset: Long) {
        month.value = month.value.plusMonths(offset)
        selectedDate.value = month.value.atDay(1)
        savedStateHandle["month"] = month.value.toString()
        refresh()
    }

    fun selectDate(date: LocalDate) { selectedDate.value = date }

    fun togglePair(symbol: String) {
        selectedPairs.value = selectedPairs.value.toMutableSet().apply {
            if (!add(symbol)) remove(symbol)
        }
    }

    fun openSettings() { settingsOpen.value = true }
    fun closeSettings() { settingsOpen.value = false }
    fun setPairVisible(symbol: String, visible: Boolean) {
        viewModelScope.launch { settingsStore.setVisible(symbol, visible) }
    }
    fun movePair(symbol: String, offset: Int) {
        viewModelScope.launch { settingsStore.move(symbol, offset, uiState.value.symbols) }
    }
    fun setQuantity(symbol: String, input: String) {
        val quantity = input.filter(Char::isDigit).toLongOrNull() ?: 0L
        viewModelScope.launch { settingsStore.setQuantity(symbol, quantity) }
    }
    fun setSide(symbol: String, side: PositionSide) {
        viewModelScope.launch { settingsStore.setSide(symbol, side) }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            message.value = null
            runCatching { repository.refresh(month.value.toString()) }
                .onFailure { message.value = it.message ?: "更新に失敗しました" }
            refreshing.value = false
        }
    }
}

private data class Selection(
    val month: YearMonth,
    val cached: jp.swapcalendar.data.CachedMonth,
    val date: LocalDate?,
    val pairs: Set<String>,
)

private data class Configuration(
    val selection: Selection,
    val settings: PairSettings,
    val settingsOpen: Boolean,
)
