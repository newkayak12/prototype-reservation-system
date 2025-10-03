package com.reservation.schedule.snapshot

import java.time.DayOfWeek
import java.time.LocalTime

class TimeSpanSnapshot(
    private val id: String? = null,
    private val restaurantId: String,
    private val day: DayOfWeek,
    private val startTime: LocalTime,
    private val endTime: LocalTime,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimeSpanSnapshot

        if (restaurantId != other.restaurantId) return false
        if (day != other.day) return false
        if (startTime != other.startTime) return false
        if (endTime != other.endTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = restaurantId.hashCode()
        result = 31 * result + day.hashCode()
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        return result
    }


}
