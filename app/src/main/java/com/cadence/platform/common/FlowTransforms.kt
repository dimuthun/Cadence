package com.cadence.platform.common

import com.cadence.domain.trigger.TriggerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

// Pure flow operators that turn device state streams into trigger events. They're kept free of
// Android types so they can be unit tested.

private object Unset

/** Emits `(previous, current)` for every value after the first. The first value is only a baseline. */
fun <T> Flow<T>.zipWithPrevious(): Flow<Pair<T, T>> = flow {
    var previous: Any? = Unset
    collect { value ->
        if (previous !== Unset) {
            @Suppress("UNCHECKED_CAST")
            emit((previous as T) to value)
        }
        previous = value
    }
}

/**
 * Emits the new level each time battery level crosses [threshold] in [direction].
 * "Falls below 20" fires on 20 → 19, and "rises above 80" fires on 80 → 81.
 */
fun Flow<Int>.thresholdCrossings(
    threshold: Int,
    direction: TriggerConfig.BatteryLevel.Direction,
): Flow<Int> = distinctUntilChanged()
    .zipWithPrevious()
    .filter { (previous, current) ->
        when (direction) {
            TriggerConfig.BatteryLevel.Direction.FALLS_BELOW -> previous >= threshold && current < threshold
            TriggerConfig.BatteryLevel.Direction.RISES_ABOVE -> previous <= threshold && current > threshold
        }
    }
    .map { (_, current) -> current }

/** Emits once each time the state changes to [target]. The initial state never emits. */
fun Flow<Boolean>.transitionsTo(target: Boolean): Flow<Unit> = distinctUntilChanged()
    .zipWithPrevious()
    .filter { (_, current) -> current == target }
    .map { }

/**
 * Turns a stream of foreground package names into launched/closed events for [packageName].
 * The initial foreground app is a baseline and never emits.
 */
fun Flow<String?>.appTransitions(packageName: String): Flow<TriggerConfig.AppLifecycle.Event> =
    distinctUntilChanged()
        .zipWithPrevious()
        .mapNotNull { (previous, current) ->
            when {
                current == packageName && previous != packageName -> TriggerConfig.AppLifecycle.Event.LAUNCHED
                previous == packageName && current != packageName -> TriggerConfig.AppLifecycle.Event.CLOSED
                else -> null
            }
        }
