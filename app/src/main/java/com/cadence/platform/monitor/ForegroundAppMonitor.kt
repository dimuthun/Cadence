package com.cadence.platform.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.core.coroutines.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/**
 * The package of the app in the foreground, polled from `UsageStatsManager` (needs usage access).
 *
 * Android has no foreground-app callback for regular apps, so this polls every
 * [POLL_INTERVAL]. To save battery it only polls while the screen is on and while some trigger
 * is subscribed. No AccessibilityService is used.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ForegroundAppMonitor @Inject constructor(
    @ApplicationContext context: Context,
    screenStateMonitor: ScreenStateMonitor,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    /** Current foreground package (`null` if unknown), replayed to new subscribers. */
    val foregroundPackage: SharedFlow<String?> = screenStateMonitor.interactive
        .distinctUntilChanged()
        .flatMapLatest { screenOn -> if (screenOn) poll() else emptyFlow() }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    /**
     * The app on screen right now, from the last few hours of usage events, or `null` when the
     * screen is off or it's unknown. For constraints; triggers use [foregroundPackage].
     */
    suspend fun currentPackage(): String? = withContext(ioDispatcher) {
        if (!powerManager.isInteractive) return@withContext null
        val now = clock.millis()
        latestResumedPackage(now - CURRENT_LOOKBACK_MILLIS, now)
    }

    private fun poll(): Flow<String?> = flow {
        var since = clock.millis() - LOOKBACK_MILLIS
        var current: String? = null
        while (true) {
            val now = clock.millis()
            current = latestResumedPackage(since, now) ?: current
            since = now
            emit(current)
            delay(POLL_INTERVAL)
        }
    }.flowOn(ioDispatcher)

    private fun latestResumedPackage(from: Long, to: Long): String? {
        val events = usageStats.queryEvents(from, to) ?: return null
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == ACTIVITY_RESUMED) latest = event.packageName
        }
        return latest
    }

    private companion object {
        val POLL_INTERVAL = 2.seconds
        const val LOOKBACK_MILLIS = 60_000L
        const val CURRENT_LOOKBACK_MILLIS = 6 * 60 * 60_000L

        /** `UsageEvents.Event.ACTIVITY_RESUMED` (API 29), same value as the older `MOVE_TO_FOREGROUND`. */
        const val ACTIVITY_RESUMED = 1
    }
}
