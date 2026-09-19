package jp.swapcalendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import jp.swapcalendar.data.PositionSide
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                SwapCalendarScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapCalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    state.updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            title = { Text("新しいバージョンがあります") },
            text = { Text("バージョン ${update.version} が公開されています。更新ページから最新版APKをインストールできます。") },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdate) { Text("あとで") } },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, update.releaseUrl.toUri()))
                    viewModel.dismissUpdate()
                }) { Text("更新ページを開く") }
            },
        )
    }
    if (state.settingsOpen) {
        PairSettingsDialog(
            state = state,
            onDismiss = viewModel::closeSettings,
            onVisibleChange = viewModel::setPairVisible,
            onMove = viewModel::movePair,
            onQuantityChange = viewModel::setQuantity,
            onSideChange = viewModel::setSide,
        )
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("スワップカレンダー") },
            actions = { TextButton(onClick = viewModel::openSettings) { Text("設定") } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthNavigation(state, { viewModel.moveMonth(-1) }, { viewModel.moveMonth(1) }, viewModel::refresh)
            if (state.symbols.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.symbols.filterNot(state.pairSettings.hidden::contains).forEach { symbol ->
                        AssistChip(
                            onClick = { viewModel.togglePair(symbol) },
                            label = { Text(if (symbol in state.selectedPairs) "✓ $symbol" else symbol) },
                        )
                    }
                }
            }
            CalendarGrid(state, viewModel::selectDate)
            MonthlySwapTotal(state)
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.rows.isEmpty() && !state.refreshing) Text("この月の保存済みデータはありません。更新してください。")
            DayDetails(state)
            SourceInfo(state)
        }
    }
}

@Composable
private fun MonthlySwapTotal(state: CalendarUiState) {
    val heldRows = state.visibleRows.filter { state.quantity(it.symbol) > 0 }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("月間スワップ合計", fontWeight = FontWeight.Bold)
            val total = state.estimatedMonthlySwap()
            Text(
                when {
                    heldRows.isEmpty() -> "保有数量未設定"
                    total == null -> "未発表"
                    else -> formatCalendarYen(total)
                },
                fontWeight = FontWeight.Bold,
                color = if (total != null && total < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun MonthNavigation(state: CalendarUiState, previous: () -> Unit, next: () -> Unit, refresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = previous) { Text("‹ 前月") }
        Text("${state.month.year}年${state.month.monthValue}月", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = next) { Text("翌月 ›") }
    }
    Button(onClick = refresh, enabled = !state.refreshing, modifier = Modifier.fillMaxWidth()) {
        if (state.refreshing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        }
        Text(if (state.refreshing) "更新中" else "最新データに更新")
    }
}

@Composable
private fun CalendarGrid(state: CalendarUiState, selectDate: (LocalDate) -> Unit) {
    val headings = listOf("月", "火", "水", "木", "金", "土", "日")
    Row(Modifier.fillMaxWidth()) { headings.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium) } }
    val leading = state.month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = List(leading) { null } + (1..state.month.lengthOfMonth()).map(state.month::atDay)
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (week + List(7 - week.size) { null }).forEach { date ->
                val rows = state.visibleRows.filter { it.tradeDate == date?.toString() }
                val selected = date == state.selectedDate
                Box(
                    Modifier.weight(1f).height(58.dp).padding(2.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp),
                        )
                        .then(if (date != null) Modifier.clickable { selectDate(date) } else Modifier)
                        .semantics { if (date != null) contentDescription = "${date.dayOfMonth}日" },
                ) {
                    if (date != null) Column(Modifier.padding(5.dp)) {
                        Text(date.dayOfMonth.toString(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (state.hasHoldings(rows)) {
                            val amount = state.estimatedDailySwap(rows)
                            Text(
                                amount?.let(::formatCalendarYen) ?: "未発表",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (amount != null && amount < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                            )
                        } else {
                            val days = rows.map { it.spDays }.distinct()
                            if (days.isNotEmpty()) {
                                Text("SP ${days.sorted().joinToString("・")}日", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCalendarYen(amount: Long): String = when {
    amount > 0 -> "+${"%,d".format(amount)}円"
    else -> "${"%,d".format(amount)}円"
}

@Composable
private fun DayDetails(state: CalendarUiState) {
    state.selectedDate?.let { Text(it.format(DateTimeFormatter.ofPattern("M月d日")), style = MaterialTheme.typography.titleMedium) }
    state.selectedRows.forEach { row ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.symbol, fontWeight = FontWeight.Bold)
                Text("SP日数: ${row.spDays}日")
                ValueRow("買（1万通貨）", row.buySwap)
                ValueRow("売（1万通貨）", row.sellSwap)
                val quantity = state.quantity(row.symbol)
                if (quantity > 0) {
                    val side = if (state.side(row.symbol) == PositionSide.BUY) "買" else "売"
                    Text("保有: ${"%,d".format(quantity)}通貨・$side")
                    val estimate = state.estimatedSwap(row)
                    Text(
                        if (estimate == null) "実額: — 未発表" else "実額: ${"%,d".format(estimate)}円",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PairSettingsDialog(
    state: CalendarUiState,
    onDismiss: () -> Unit,
    onVisibleChange: (String, Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onQuantityChange: (String, String) -> Unit,
    onSideChange: (String, PositionSide) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通貨ペア設定") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("上下ボタンで表示優先度を変更できます。保有数量は通貨単位で入力してください。", style = MaterialTheme.typography.bodySmall)
                state.symbols.forEachIndexed { index, symbol ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = symbol !in state.pairSettings.hidden,
                                    onCheckedChange = { onVisibleChange(symbol, it) },
                                )
                                Text(symbol, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onMove(symbol, -1) }, enabled = index > 0) { Text("↑") }
                                TextButton(onClick = { onMove(symbol, 1) }, enabled = index < state.symbols.lastIndex) { Text("↓") }
                            }
                            QuantityField(symbol, state.quantity(symbol), onQuantityChange)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.side(symbol) == PositionSide.BUY,
                                    onClick = { onSideChange(symbol, PositionSide.BUY) },
                                    label = { Text("買ポジション") },
                                )
                                FilterChip(
                                    selected = state.side(symbol) == PositionSide.SELL,
                                    onClick = { onSideChange(symbol, PositionSide.SELL) },
                                    label = { Text("売ポジション") },
                                )
                            }
                        }
                    }
                }
                Text("実額 =（保有通貨数量 ÷ 10,000）× 掲載スワップ。受取・支払ともGMO外貨の端数処理に合わせて円単位へ丸めます。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("完了") } },
    )
}

@Composable
private fun QuantityField(symbol: String, persisted: Long, onChange: (String, String) -> Unit) {
    var text by remember(symbol) { mutableStateOf(if (persisted > 0) persisted.toString() else "") }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.filter(Char::isDigit).take(12)
            onChange(symbol, text)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("保有通貨数量") },
        suffix = { Text("通貨") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun ValueRow(label: String, value: String?) {
    val shown = value ?: "— 未発表"
    val color = when {
        value == null -> MaterialTheme.colorScheme.onSurfaceVariant
        value.startsWith("-") -> MaterialTheme.colorScheme.error
        else -> Color.Unspecified
    }
    Text("$label: $shown", color = color)
}

@Composable
private fun SourceInfo(state: CalendarUiState) {
    val context = LocalContext.current
    Spacer(Modifier.height(8.dp))
    state.sourceFetchedAt?.let { Text("公式データ確認: $it", style = MaterialTheme.typography.bodySmall) }
    state.lastSyncedAt?.let { Text("端末同期: $it", style = MaterialTheme.typography.bodySmall) }
    Text("掲載値は変更される場合があります。", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.gaikaex.com/gaikaex/mark/swap/calendar.php".toUri()))
    }) { Text("出典: GMO外貨") }
}
