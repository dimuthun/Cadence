package com.cadence.domain.trigger

import java.time.Instant

/**
 * A single occurrence of a trigger.
 *
 * @property source the configuration that matched.
 * @property occurredAt when the underlying system event happened.
 * @property data event details for the log and, in Phase 2, for variables
 * (e.g. `"package"`, `"battery"`).
 * @property id stable id for events that may be delivered more than once, e.g. an alarm or boot
 * event replayed to a trigger that registered after the process was woken. The engine runs a
 * macro at most once per id. `null` for events that are never replayed.
 */
data class TriggerEvent(
    val source: TriggerConfig,
    val occurredAt: Instant,
    val data: Map<String, String> = emptyMap(),
    val id: String? = null,
)
