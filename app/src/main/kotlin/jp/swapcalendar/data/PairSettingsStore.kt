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
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairSettingsDataStore by preferencesDataStore("pair_settings")
private val settingsKey = stringPreferencesKey("settings_json")

@Serializable
enum class PositionSide { BUY, SELL }

@Serializable
data class PairSettings(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val quantities: Map<String, Long> = emptyMap(),
    val sides: Map<String, PositionSide> = emptyMap(),
) {
    fun orderedSymbols(available: List<String>): List<String> =
        order.filter(available::contains) + available.filterNot(order::contains)
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
        current.copy(quantities = current.quantities.toMutableMap().apply {
            if (quantity <= 0) remove(symbol) else put(symbol, quantity)
        })
    }

    suspend fun setSide(symbol: String, side: PositionSide) = update { current ->
        current.copy(sides = current.sides + (symbol to side))
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
