package jp.swapcalendar.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "currency_pairs", primaryKeys = ["id"])
data class CurrencyPairEntity(val id: String, val symbol: String, val displayOrder: Int)

@Entity(tableName = "swap_points", primaryKeys = ["pairId", "tradeDate"])
data class SwapPointEntity(
    val pairId: String,
    val month: String,
    val tradeDate: String,
    val spDays: Int,
    val buySwap: String?,
    val sellSwap: String?,
    val publicationState: String,
)

@Entity(tableName = "cached_months", primaryKeys = ["month"])
data class CachedMonthEntity(val month: String, val sourceFetchedAt: String, val lastSyncedAt: String)

data class SwapPointRow(
    val pairId: String,
    val symbol: String,
    val tradeDate: String,
    val spDays: Int,
    val buySwap: String?,
    val sellSwap: String?,
    val publicationState: String,
)

@Dao
abstract class SwapDao {
    @Query("""
        SELECT sp.pairId, cp.symbol, sp.tradeDate, sp.spDays, sp.buySwap, sp.sellSwap, sp.publicationState
        FROM swap_points sp JOIN currency_pairs cp ON cp.id = sp.pairId
        WHERE sp.month = :month ORDER BY sp.tradeDate, cp.displayOrder
    """)
    abstract fun observeMonth(month: String): Flow<List<SwapPointRow>>

    @Query("SELECT * FROM cached_months WHERE month = :month")
    abstract fun observeMetadata(month: String): Flow<CachedMonthEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPairs(pairs: List<CurrencyPairEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPoints(points: List<SwapPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMonth(month: CachedMonthEntity)

    @Query("DELETE FROM swap_points WHERE month = :month")
    protected abstract suspend fun deleteMonth(month: String)

    @Transaction
    open suspend fun replaceMonth(
        month: CachedMonthEntity,
        pairs: List<CurrencyPairEntity>,
        points: List<SwapPointEntity>,
    ) {
        insertPairs(pairs)
        deleteMonth(month.month)
        insertPoints(points)
        insertMonth(month)
    }
}

@Database(
    entities = [CurrencyPairEntity::class, SwapPointEntity::class, CachedMonthEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SwapDatabase : RoomDatabase() { abstract fun swapDao(): SwapDao }

