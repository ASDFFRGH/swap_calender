package jp.swapcalendar.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import jp.swapcalendar.data.ApiException
import jp.swapcalendar.data.SwapRepository
import java.time.Duration
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: SwapRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val current = YearMonth.now(ZoneId.of("Asia/Tokyo"))
        repository.refresh(current.toString())
        repository.refresh(current.minusMonths(1).toString())
        Result.success()
    } catch (exception: ApiException) {
        if (exception.code == "MONTH_NOT_AVAILABLE" || exception.code == "MONTH_OUT_OF_RANGE") Result.success()
        else Result.retry()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "swap-calendar-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(10))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

