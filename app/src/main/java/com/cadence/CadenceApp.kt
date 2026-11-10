package com.cadence

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.cadence.platform.notification.CadenceNotifications
import com.cadence.platform.work.LogRetentionWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Supplies WorkManager with a Hilt-aware [HiltWorkerFactory] so workers can use constructor
 * injection. The default WorkManager initializer is disabled in the manifest for this reason.
 * The engine service is *not* started here: the process may be starting in the background,
 * where Android forbids starting foreground services.
 */
@HiltAndroidApp
class CadenceApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notifications: CadenceNotifications

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        LogRetentionWorker.schedule(WorkManager.getInstance(this))
    }
}
