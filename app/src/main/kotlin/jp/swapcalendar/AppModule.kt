package jp.swapcalendar

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import jp.swapcalendar.data.SwapApi
import jp.swapcalendar.data.SwapDao
import jp.swapcalendar.data.SwapDatabase
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SwapDatabase =
        Room.databaseBuilder(context, SwapDatabase::class.java, "swap-calendar.db").build()

    @Provides fun dao(database: SwapDatabase): SwapDao = database.swapDao()

    @Provides @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = false; explicitNulls = true })
        }
    }

    @Provides @Singleton
    fun api(client: HttpClient): SwapApi = SwapApi(client, BuildConfig.API_BASE_URL)
}

