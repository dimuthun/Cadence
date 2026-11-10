package com.cadence.platform.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Daily deletion of log entries older than the retention setting. */
@HiltWorker
class LogRetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val executionLogRepository: ExecutionLogRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val days = settingsRepository.settings.first().logRetentionDays
        executionLogRepository.deleteOlderThan(clock.instant().minus(Duration.ofDays(days.toLong())))
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "log-retention"

        /** Schedules the daily cleanup. Keeps an existing schedule. */
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<LogRetentionWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
