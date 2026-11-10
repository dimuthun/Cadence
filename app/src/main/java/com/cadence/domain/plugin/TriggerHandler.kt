package com.cadence.domain.plugin

import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

/**
 * Listens for one trigger type. Implement this and register it with Hilt to add a trigger;
 * the engine needs no changes.
 *
 * The registration lifecycle is the lifecycle of the [Flow] returned by [observe]:
 * - **register** when collection starts (register receivers, callbacks, or alarms);
 * - **emit** a [TriggerEvent] each time the event matches the config;
 * - **unregister** when collection is cancelled, in `awaitClose {}` or `finally {}`.
 *
 * Because unregistering is tied to coroutine cancellation, disabling a macro, editing it, or
 * stopping the engine always releases system resources. Handlers that share one system
 * registration across many configs (e.g. one battery receiver) should share a hot upstream
 * with `shareIn(scope, SharingStarted.WhileSubscribed())`, so it is dropped once the last
 * collector leaves.
 *
 * @param C the config type this handler owns.
 */
interface TriggerHandler<C : TriggerConfig> {

    /** The config type this handler owns. Each type can have only one registered handler. */
    val configType: KClass<C>

    /** Permissions [config] needs. If any is missing the engine does not call [observe]. */
    fun requiredPermissions(config: C): Set<PermissionRequirement> = emptySet()

    /**
     * Returns a cold flow of events matching [config]. Collecting it registers the trigger and
     * cancelling the collection must unregister it. Exceptions end only this trigger; the
     * engine reports them and keeps running.
     */
    fun observe(config: C): Flow<TriggerEvent>
}
