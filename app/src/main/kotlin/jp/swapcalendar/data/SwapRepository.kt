package jp.swapcalendar.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class CachedMonth(val rows: List<SwapPointRow>, val metadata: CachedMonthEntity?)

@Singleton
class SwapRepository @Inject constructor(private val dao: SwapDao, private val api: SwapApi) {
    fun observeMonth(month: String): Flow<CachedMonth> =
        combine(dao.observeMonth(month), dao.observeMetadata(month), ::CachedMonth)

    suspend fun refresh(month: String) {
        val response = api.month(month)
        require(response.month == month) { "応答の年月が一致しません" }
        val pairs = response.pairs.mapIndexed { index, pair -> CurrencyPairEntity(pair.id, pair.symbol, index) }
        val points = response.pairs.flatMap { pair ->
            pair.points.map { point ->
                require(point.tradeDate.startsWith(month)) { "対象月外の日付です" }
                SwapPointEntity(
                    pair.id, month, point.tradeDate, point.spDays,
                    point.buySwap, point.sellSwap, point.publicationState.name,
                )
            }
        }
        require(points.distinctBy { it.pairId to it.tradeDate }.size == points.size) { "重複データです" }
        dao.replaceMonth(
            CachedMonthEntity(month, response.source.lastFetchedAt, Instant.now().toString()),
            pairs,
            points,
        )
    }
}

