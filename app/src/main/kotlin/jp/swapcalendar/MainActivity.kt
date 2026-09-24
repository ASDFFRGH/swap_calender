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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.launch

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
fun SwapCalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    leverageViewModel: LeverageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val leverageState by leverageViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(AppDestination.CALENDAR) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("スワップカレンダー", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
                NavigationDrawerItem(
                    label = { Text("カレンダー") },
                    selected = destination == AppDestination.CALENDAR,
                    onClick = { destination = AppDestination.CALENDAR; scope.launch { drawerState.close() } },
                )
                NavigationDrawerItem(
                    label = { Text("保有数量計算") },
                    selected = destination == AppDestination.LEVERAGE,
                    onClick = { destination = AppDestination.LEVERAGE; scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Scaffold(topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰") } },
                title = { Text(if (destination == AppDestination.CALENDAR) "スワップカレンダー" else "保有数量計算") },
                actions = {
                    if (destination == AppDestination.CALENDAR) {
                        TextButton(onClick = viewModel::openSettings) { Text("設定") }
                    }
                },
            )
        }) { padding ->
            if (destination == AppDestination.CALENDAR) {
                CalendarContent(state, viewModel, Modifier.padding(padding))
            } else {
                LeverageCalculatorScreen(leverageState, leverageViewModel, Modifier.padding(padding))
            }
        }
    }
}

private enum class AppDestination { CALENDAR, LEVERAGE }

@Composable
private fun CalendarContent(state: CalendarUiState, viewModel: CalendarViewModel, modifier: Modifier = Modifier) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeverageCalculatorScreen(
    state: LeverageUiState,
    viewModel: LeverageViewModel,
    modifier: Modifier = Modifier,
) {
    var pairMenuOpen by remember { mutableStateOf(false) }
    val selectedRate = state.rates.firstOrNull { it.symbol == state.selectedSymbol }
    val baseCurrency = state.selectedSymbol?.substringBefore("/")
    val quoteCurrency = state.selectedSymbol?.substringAfter("/")
    val baseJpyRate = state.rates.firstOrNull { it.symbol == "$baseCurrency/JPY" }?.midpoint
    val quoteJpyRate = when (quoteCurrency) {
        "JPY" -> BigDecimal.ONE
        null -> null
        else -> state.rates.firstOrNull { it.symbol == "$quoteCurrency/JPY" }?.midpoint
    }
    val positions = state.pairSettings.quantities.mapNotNull { (symbol, quantity) ->
        val currency = symbol.substringBefore("/")
        val yenRate = if (currency == "JPY") BigDecimal.ONE else state.rates.firstOrNull { it.symbol == "$currency/JPY" }?.midpoint
        yenRate?.let {
            PositionValuation(
                symbol = symbol,
                quantity = quantity,
                exposure = it.multiply(BigDecimal.valueOf(quantity)),
                unrealizedPnl = state.unrealizedPnls[symbol]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            )
        }
    }
    val calculation = baseJpyRate?.let {
        calculatePortfolio(
            deposit = state.deposit.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            positions = positions,
            legacyUnrealizedLoss = state.unrealizedLoss.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            targetLeverage = state.targetLeverage.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            additionalBaseJpyRate = it,
        )
    }
    val selectedQuantity = state.selectedSymbol?.let { state.pairSettings.quantities[it] } ?: 0L
    val selectedSide = state.selectedSymbol?.let { state.pairSettings.sides[it] } ?: state.side
    val lossCut = if (calculation != null && selectedRate != null && quoteJpyRate != null) {
        estimatePortfolioLossCut(calculation, selectedQuantity, selectedRate.midpoint, quoteJpyRate, selectedSide)
    } else null

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("複数通貨ペアの保有数量と含み損益から、口座全体のレバレッジと追加保有可能数量を計算します。")
        ExposedDropdownMenuBox(expanded = pairMenuOpen, onExpandedChange = { pairMenuOpen = it }) {
            OutlinedTextField(
                value = state.selectedSymbol ?: "",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                label = { Text("通貨ペア") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pairMenuOpen) },
            )
            ExposedDropdownMenu(expanded = pairMenuOpen, onDismissRequest = { pairMenuOpen = false }) {
                state.rates.forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(rate.symbol) },
                        onClick = { viewModel.select(rate.symbol); pairMenuOpen = false },
                    )
                }
            }
        }
        MoneyInput(state.deposit, { viewModel.setDeposit(it) }, "現在の入金額")
        if (state.unrealizedLoss.isNotBlank()) {
            MoneyInput(state.unrealizedLoss, { viewModel.setUnrealizedLoss(it) }, "旧入力の含み損（口座全体）")
        }
        selectedRate?.let { rate ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${rate.symbol} の保有状況", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = selectedQuantity.takeIf { it > 0 }?.toString() ?: "",
                        onValueChange = { viewModel.setPositionQuantity(rate.symbol, it) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("保有数量") }, suffix = { Text("通貨") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = state.unrealizedPnls[rate.symbol] ?: "",
                        onValueChange = { viewModel.setUnrealizedPnl(rate.symbol, signedDecimalInput(it)) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("含み損益（損失はマイナス）") }, suffix = { Text("円") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = selectedSide == PositionSide.BUY, onClick = { viewModel.setPositionSide(rate.symbol, PositionSide.BUY) }, label = { Text("買い") })
                        FilterChip(selected = selectedSide == PositionSide.SELL, onClick = { viewModel.setPositionSide(rate.symbol, PositionSide.SELL) }, label = { Text("売り") })
                    }
                }
            }
        }
        if (positions.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("保有ポジション（${positions.size}通貨ペア）", fontWeight = FontWeight.Bold)
                    positions.forEach { position ->
                        Text("${position.symbol}: ${"%,d".format(position.quantity)}通貨 / 含み損益 ${"%,.0f".format(position.unrealizedPnl)}円")
                    }
                }
            }
        }
        OutlinedTextField(
            value = state.targetLeverage,
            onValueChange = { viewModel.setTargetLeverage(decimalInput(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("目標レバレッジ") },
            suffix = { Text("倍") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("計算結果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (calculation != null) {
                    Text("現在レバレッジ: ${calculation.currentLeverage.stripTrailingZeros().toPlainString()}倍", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("有効資産: ${"%,.0f".format(calculation.effectiveEquity)}円")
                    Text("現在の総保有額: ${"%,.0f".format(calculation.totalExposure)}円")
                    Text("目標までの追加保有余力: ${"%,.0f".format(calculation.remainingExposure)}円")
                    Text("${state.selectedSymbol} の追加可能数量: ${"%,d".format(calculation.additionalQuantity)}通貨", fontWeight = FontWeight.Bold)
                    Text("追加後合計: ${"%,d".format(selectedQuantity + calculation.additionalQuantity)}通貨")
                    lossCut?.let { estimate ->
                        Text(
                            "${state.selectedSymbol}のみが変動した場合の推定ロスカットレート: ${formatRate(estimate.triggerRate)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            if (selectedSide == PositionSide.BUY) {
                                "現在値から約 ${formatRate(estimate.rateDistance)} 下落"
                            } else {
                                "現在値から約 ${formatRate(estimate.rateDistance)} 上昇"
                            },
                        )
                        Text("ロスカットまでの追加損失余力: ${"%,.0f".format(estimate.lossAllowance)}円")
                    }
                } else {
                    Text(if (state.deposit.isNotBlank() && state.unrealizedLoss.isNotBlank() && state.targetLeverage.isNotBlank()) "入力値または円換算レートを確認してください。" else "金額と目標レバレッジを入力してください。")
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("取得時点の為替相場", fontWeight = FontWeight.Bold)
                selectedRate?.let { Text("${it.symbol}: Bid ${it.bid.toPlainString()} / Ask ${it.ask.toPlainString()}") }
                baseJpyRate?.let { Text("円換算レート: 1 $baseCurrency = ${it.stripTrailingZeros().toPlainString()}円（Bid/Ask中間値）") }
                state.fetchedAt?.let { Text("取得日時: ${formatRateTimestamp(it)}", style = MaterialTheme.typography.bodySmall) }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = viewModel::refresh, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (state.loading) " 取得中" else "為替相場を更新")
                }
            }
        }
        Text("有効資産は入金額と全ポジションの含み損益から、総保有額は各基準通貨の円換算額から算出します。追加可能数量は目標レバレッジまでの余力を選択通貨の円換算レートで割り、1通貨未満を切り捨てます。ロスカットは個人口座の維持証拠金率4%・維持率50%を前提に、選択した通貨ペアだけが変動する場合の概算です。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MoneyInput(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(decimalInput(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text("円") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun decimalInput(value: String): String = value.filter { it.isDigit() || it == '.' }
private fun signedDecimalInput(value: String): String = value.filterIndexed { index, char -> char.isDigit() || char == '.' || (char == '-' && index == 0) }
    .let { filtered -> if (filtered.count { it == '.' } <= 1) filtered else filtered.substringBeforeLast('.') }
    .take(16)

private fun formatRateTimestamp(raw: String): String = if (raw.length >= 14) {
    "${raw.substring(0, 4)}/${raw.substring(4, 6)}/${raw.substring(6, 8)} ${raw.substring(8, 10)}:${raw.substring(10, 12)}:${raw.substring(12, 14)}"
} else raw

private fun formatRate(value: BigDecimal): String = value.setScale(5, RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()

@Composable
private fun MonthlySwapTotal(state: CalendarUiState) {
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
                    state.heldRows.isEmpty() -> "保有数量未設定"
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
    val headings = listOf("日", "月", "火", "水", "木", "金", "土")
    Row(Modifier.fillMaxWidth()) { headings.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium) } }
    val leading = sundayFirstLeadingDays(state.month)
    val cells = List(leading) { null } + (1..state.month.lengthOfMonth()).map(state.month::atDay)
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (week + List(7 - week.size) { null }).forEach { date ->
                val visibleRows = state.visibleRows.filter { it.tradeDate == date?.toString() }
                val heldRows = state.heldRows.filter { it.tradeDate == date?.toString() }
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
                        if (heldRows.isNotEmpty()) {
                            val amount = state.estimatedDailySwap(heldRows)
                            Text(
                                amount?.let(::formatCalendarYen) ?: "未発表",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (amount != null && amount < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                            )
                        } else {
                            val days = visibleRows.map { it.spDays }.distinct()
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

internal fun sundayFirstLeadingDays(month: YearMonth): Int = month.atDay(1).dayOfWeek.value % 7

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
                if (state.heldSymbols.isNotEmpty()) {
                    Text(
                        "保有中（${state.heldSymbols.size}通貨ペア）: ${state.heldSymbols.joinToString { symbol -> "$symbol ${"%,d".format(state.quantity(symbol))}通貨" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
