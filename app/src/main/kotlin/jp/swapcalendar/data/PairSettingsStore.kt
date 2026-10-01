package jp.swapcalendar.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairSettingsDataStore by preferencesDataStore("pair_settings")
private val settingsKey = stringPreferencesKey("settings_json")

@Serializable
enum class PositionSide { BUY, SELL }

@Serializable
data class ClosedHolding(
    val symbol: String,
    val quantity: Long,
    val side: PositionSide,
    val heldOn: String,
    val releasedOn: String,
)

data class Holding(val quantity: Long, val side: PositionSide)

@Serializable
data class PairSettings(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val quantities: Map<String, Long> = emptyMap(),
    val sides: Map<String, PositionSide> = emptyMap(),
    val heldOn: Map<String, String> = emptyMap(),
    val closedHoldings: List<ClosedHolding> = emptyList(),
) {
    fun orderedSymbols(available: List<String>): List<String> =
        order.filter(available::contains) + available.filterNot(order::contains)

    fun holdingsOn(symbol: String, date: LocalDate): List<Holding> = buildList {
        closedHoldings.filter { it.symbol == symbol &&
            !date.isBefore(LocalDate.parse(it.heldOn)) &&
            !date.isAfter(LocalDate.parse(it.releasedOn))
        }.forEach { add(Holding(it.quantity, it.side)) }
        val quantity = quantities[symbol] ?: 0L
        val start = heldOn[symbol]?.let(LocalDate::parse)
        if (quantity > 0 && (start == null || !date.isBefore(start))) {
            add(Holding(quantity, sides[symbol] ?: PositionSide.BUY))
        }
    }

}

@Singleton
class PairSettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<PairSettings> = context.pairSettingsDataStore.data
        .map { preferences ->
            preferences[settingsKey]?.let { value ->
                runCatching { json.decodeFromString<PairSettings>(value) }.getOrDefault(PairSettings())
            } ?: PairSettings()
        }
        .catch { exception ->
            if (exception is IOException) emit(PairSettings()) else throw exception
        }

    suspend fun setVisible(symbol: String, visible: Boolean) = update { current ->
        current.copy(hidden = current.hidden.toMutableSet().apply {
            if (visible) remove(symbol) else add(symbol)
        })
    }

    suspend fun move(symbol: String, offset: Int, available: List<String>) = update { current ->
        val order = current.orderedSymbols(available).toMutableList()
        val from = order.indexOf(symbol)
        val to = (from + offset).coerceIn(0, order.lastIndex)
        if (from >= 0 && from != to) {
            order.removeAt(from)
            order.add(to, symbol)
        }
        current.copy(order = order)
    }

    suspend fun setQuantity(symbol: String, quantity: Long) = update { current ->
        current.copy(
            quantities = current.quantities.toMutableMap().apply {
                if (quantity <= 0) remove(symbol) else put(symbol, quantity)
            },
            heldOn = current.heldOn.toMutableMap().apply {
                if (quantity <= 0) remove(symbol)
                else if (current.quantities[symbol] == null && this[symbol] == null) {
                    put(symbol, LocalDate.now(ZoneId.of("Asia/Tokyo")).toString())
                }
            },
        )
    }

    suspend fun setSide(symbol: String, side: PositionSide) = update { current ->
        current.copy(sides = current.sides + (symbol to side))
    }

    suspend fun setHeldOn(symbol: String, date: LocalDate) = update { current ->
        if ((current.quantities[symbol] ?: 0L) <= 0) current
        else current.copy(heldOn = current.heldOn + (symbol to date.toString()))
    }

    suspend fun release(symbol: String, date: LocalDate) = update { current ->
        val quantity = current.quantities[symbol] ?: 0L
        val start = current.heldOn[symbol]?.let(LocalDate::parse)
        if (quantity <= 0 || start == null || date.isBefore(start) || date.isAfter(LocalDate.now(ZoneId.of("Asia/Tokyo")))) current
        else current.copy(
            quantities = current.quantities - symbol,
            heldOn = current.heldOn - symbol,
            closedHoldings = current.closedHoldings + ClosedHolding(
                symbol, quantity, current.sides[symbol] ?: PositionSide.BUY,
                start.toString(), date.toString(),
            ),
        )
    }

    suspend fun removeClosedHolding(index: Int) = update { current ->
        if (index !in current.closedHoldings.indices) current
        else current.copy(closedHoldings = current.closedHoldings.filterIndexed { i, _ -> i != index })
    }

    private suspend fun update(transform: (PairSettings) -> PairSettings) {
        context.pairSettingsDataStore.edit { preferences ->
            val current = preferences[settingsKey]?.let { value ->
                runCatching { json.decodeFromString<PairSettings>(value) }.getOrDefault(PairSettings())
            } ?: PairSettings()
            preferences[settingsKey] = json.encodeToString(transform(current))
        }
    }
}
