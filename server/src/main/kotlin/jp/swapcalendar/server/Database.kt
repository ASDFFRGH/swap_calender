package jp.swapcalendar.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jp.swapcalendar.api.AvailabilityResponseDto
import jp.swapcalendar.api.CurrencyPairDto
import jp.swapcalendar.api.CurrencyPairsResponseDto
import jp.swapcalendar.api.MonthResponseDto
import jp.swapcalendar.api.PairPointsDto
import jp.swapcalendar.api.PublicationStateDto
import jp.swapcalendar.api.SourceDto
import jp.swapcalendar.api.SwapPointDto
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.YearMonth

class Database private constructor(private val dataSource: HikariDataSource) : AutoCloseable {
    companion object {
        fun connect(url: String, user: String, password: String): Database {
            val config = HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = 5
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
            }
            val source = HikariDataSource(config)
            Flyway.configure().dataSource(source).load().migrate()
            return Database(source)
        }
    }

    fun import(parsed: ParsedMonth, fetchedAt: OffsetDateTime) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val pairIds = parsed.points.distinctBy { it.sourcePairId }.associate { point ->
                    point.sourcePairId to connection.upsertPair(point, fetchedAt)
                }
                connection.prepareStatement(
                    """
                    INSERT INTO swap_points(currency_pair_id, trade_date, sp_days, buy_swap, sell_swap,
                      publication_state, source_month, first_fetched_at, last_fetched_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(currency_pair_id, trade_date) DO UPDATE SET
                      sp_days = EXCLUDED.sp_days,
                      buy_swap = COALESCE(EXCLUDED.buy_swap, swap_points.buy_swap),
                      sell_swap = COALESCE(EXCLUDED.sell_swap, swap_points.sell_swap),
                      publication_state = CASE
                        WHEN EXCLUDED.publication_state = 'UNPUBLISHED' AND swap_points.publication_state = 'PUBLISHED'
                        THEN swap_points.publication_state ELSE EXCLUDED.publication_state END,
                      last_fetched_at = EXCLUDED.last_fetched_at,
                      updated_at = CASE WHEN
                        swap_points.sp_days IS DISTINCT FROM EXCLUDED.sp_days OR
                        (EXCLUDED.buy_swap IS NOT NULL AND swap_points.buy_swap IS DISTINCT FROM EXCLUDED.buy_swap) OR
                        (EXCLUDED.sell_swap IS NOT NULL AND swap_points.sell_swap IS DISTINCT FROM EXCLUDED.sell_swap)
                        THEN EXCLUDED.updated_at ELSE swap_points.updated_at END
                    """.trimIndent(),
                ).use { statement ->
                    parsed.points.forEach { point ->
                        statement.setLong(1, pairIds.getValue(point.sourcePairId))
                        statement.setObject(2, point.tradeDate)
                        statement.setInt(3, point.spDays)
                        statement.setBigDecimal(4, point.buySwap)
                        statement.setBigDecimal(5, point.sellSwap)
                        statement.setString(6, point.state.name)
                        statement.setString(7, parsed.month.toCompact())
                        statement.setObject(8, fetchedAt)
                        statement.setObject(9, fetchedAt)
                        statement.setObject(10, fetchedAt)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            }
        }
    }

    private fun Connection.upsertPair(point: ParsedPoint, now: OffsetDateTime): Long =
        prepareStatement(
            """
            INSERT INTO currency_pairs(source_pair_id, symbol, display_order, is_active, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, TRUE, ?, ?)
            ON CONFLICT(source_pair_id) DO UPDATE SET symbol=EXCLUDED.symbol,
              display_order=EXCLUDED.display_order, is_active=TRUE, last_seen_at=EXCLUDED.last_seen_at
            RETURNING id
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, point.sourcePairId)
            statement.setString(2, point.symbol)
            statement.setInt(3, point.displayOrder)
            statement.setObject(4, now)
            statement.setObject(5, now)
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }

    fun month(month: YearMonth): MonthResponseDto? = dataSource.connection.use { connection ->
        val sql = """
            SELECT cp.id, cp.symbol, sp.trade_date, sp.sp_days, sp.buy_swap, sp.sell_swap,
                   sp.publication_state, MAX(sp.last_fetched_at) OVER () AS fetched_at
            FROM swap_points sp JOIN currency_pairs cp ON cp.id = sp.currency_pair_id
            WHERE sp.source_month = ? ORDER BY cp.display_order, sp.trade_date
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, month.toCompact())
            statement.executeQuery().use { result ->
                val rows = mutableListOf<Pair<String, SwapPointDto>>()
                val symbols = linkedMapOf<String, String>()
                var fetchedAt: OffsetDateTime? = null
                while (result.next()) {
                    val id = result.getLong("id").toString()
                    symbols[id] = result.getString("symbol")
                    fetchedAt = result.getObject("fetched_at", OffsetDateTime::class.java)
                    rows += id to SwapPointDto(
                        tradeDate = result.getObject("trade_date", java.time.LocalDate::class.java).toString(),
                        spDays = result.getInt("sp_days"),
                        buySwap = result.getBigDecimal("buy_swap")?.toPlainString(),
                        sellSwap = result.getBigDecimal("sell_swap")?.toPlainString(),
                        publicationState = PublicationStateDto.valueOf(result.getString("publication_state")),
                    )
                }
                if (rows.isEmpty()) return@use null
                MonthResponseDto(
                    month = month.toString(),
                    source = SourceDto("GMO外貨", SOURCE_URL, fetchedAt!!.toString()),
                    pairs = rows.groupBy({ it.first }, { it.second }).map { (id, points) ->
                        PairPointsDto(id, symbols.getValue(id), points)
                    },
                )
            }
        }
    }

    fun pairs(): CurrencyPairsResponseDto = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id, symbol, display_order, is_active FROM currency_pairs ORDER BY display_order").use { statement ->
            statement.executeQuery().use { result ->
                val pairs = buildList {
                    while (result.next()) add(CurrencyPairDto(
                        result.getLong("id").toString(), result.getString("symbol"),
                        result.getInt("display_order"), result.getBoolean("is_active"),
                    ))
                }
                CurrencyPairsResponseDto(pairs)
            }
        }
    }

    fun availability(): AvailabilityResponseDto = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT DISTINCT source_month FROM swap_points ORDER BY source_month").use { statement ->
            statement.executeQuery().use { result ->
                val months = buildList {
                    while (result.next()) {
                        val value = result.getString(1)
                        add("${value.take(4)}-${value.takeLast(2)}")
                    }
                }
                AvailabilityResponseDto("2014-12", months)
            }
        }
    }

    override fun close() = dataSource.close()
}

internal const val SOURCE_URL = "https://www.gaikaex.com/gaikaex/mark/swap/calendar.php"
internal fun YearMonth.toCompact() = "%04d%02d".format(year, monthValue)
internal fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

