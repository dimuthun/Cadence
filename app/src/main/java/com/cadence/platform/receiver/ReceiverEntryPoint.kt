package com.cadence.platform.receiver

import com.cadence.core.coroutines.ApplicationScope
import com.cadence.platform.events.ExternalTriggerBus
import com.cadence.platform.service.EngineServiceController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/**
 * Dependencies for manifest broadcast receivers. Receivers use an entry point instead of
 * `@AndroidEntryPoint` because Hilt's receiver injection needs a `super.onReceive` call, which
 * Kotlin can't make on the abstract base method.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun externalTriggerBus(): ExternalTriggerBus
    fun engineServiceController(): EngineServiceController

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}
