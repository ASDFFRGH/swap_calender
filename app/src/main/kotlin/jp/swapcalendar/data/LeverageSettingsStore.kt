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

private val Context.leverageSettingsDataStore by preferencesDataStore("leverage_calculator_settings")
private val leverageSettingsKey = stringPreferencesKey("settings_json")

@Serializable
data class LeverageSettings(
    val symbol: String? = null,
    val deposit: String = "",
    val unrealizedLoss: String = "",
    val targetLeverage: String = "",
    val side: PositionSide = PositionSide.BUY,
)

@Singleton
class LeverageSettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<LeverageSettings> = context.leverageSettingsDataStore.data
        .map { preferences ->
            preferences[leverageSettingsKey]?.let { value ->
                runCatching { json.decodeFromString<LeverageSettings>(value) }.getOrDefault(LeverageSettings())
            } ?: LeverageSettings()
        }
        .catch { exception ->
            if (exception is IOException) emit(LeverageSettings()) else throw exception
        }

    suspend fun update(settings: LeverageSettings) {
        context.leverageSettingsDataStore.edit { preferences ->
            preferences[leverageSettingsKey] = json.encodeToString(settings)
        }
    }
}
