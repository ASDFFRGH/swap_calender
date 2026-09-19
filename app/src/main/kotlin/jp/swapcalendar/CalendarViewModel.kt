package jp.swapcalendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.swapcalendar.data.SwapPointRow
import jp.swapcalendar.data.SwapRepository
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
) {
    val symbols: List<String> get() = rows.map { it.symbol }.distinct()
    val visibleRows: List<SwapPointRow> get() = if (selectedPairs.isEmpty()) rows else rows.filter { it.symbol in selectedPairs }
    val selectedRows: List<SwapPointRow> get() = visibleRows.filter { it.tradeDate == selectedDate?.toString() }
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: SwapRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val initialMonth = savedStateHandle.get<String>("month")?.let(YearMonth::parse)
        ?: YearMonth.now(ZoneId.of("Asia/Tokyo"))
    private val month = MutableStateFlow(initialMonth)
    private val selectedDate = MutableStateFlow<LocalDate?>(initialMonth.atDay(1))
    private val selectedPairs = MutableStateFlow<Set<String>>(emptySet())
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val cached = month.flatMapLatest { repository.observeMonth(it.toString()) }
    private val selection = combine(month, cached, selectedDate, selectedPairs, ::Selection)
    val uiState: StateFlow<CalendarUiState> = combine(selection, refreshing, message) { selection, isRefreshing, currentMessage ->
        CalendarUiState(
            month = selection.month,
            rows = selection.cached.rows,
            selectedDate = selection.date,
            selectedPairs = selection.pairs,
            sourceFetchedAt = selection.cached.metadata?.sourceFetchedAt,
            lastSyncedAt = selection.cached.metadata?.lastSyncedAt,
            refreshing = isRefreshing,
            message = currentMessage,
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
