package com.cadence.domain.model

import java.time.DayOfWeek
import kotlinx.serialization.Serializable

/**
 * Day of the week as stored in macro configs.
 *
 * A domain-owned enum is used instead of [DayOfWeek] because `java.time` types have no
 * kotlinx.serialization serializer. Constant names are part of the storage format: do not rename.
 */
@Serializable
enum class Weekday {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    /** The equivalent `java.time` value. */
    val dayOfWeek: DayOfWeek get() = DayOfWeek.of(ordinal + 1)

    companion object {
        /** Converts a `java.time` day into a [Weekday]. */
        fun from(day: DayOfWeek): Weekday = entries[day.value - 1]
    }
}
