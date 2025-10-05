package com.reservation.schedule

import com.reservation.schedule.snapshot.TimeSpanSnapshot
import java.time.DayOfWeek
import java.time.LocalTime

class TimeSpan(
    private val id: String? = null,
    private val restaurantId: String,
    private val day: DayOfWeek,
    private val startTime: LocalTime,
    private val endTime: LocalTime,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimeSpan

        if (day != other.day) return false
        if (startTime != other.startTime) return false
        if (endTime != other.endTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result =  day.hashCode()
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        return result
    }

    fun snapshot() =
        TimeSpanSnapshot(
            id = id,
            restaurantId = restaurantId,
            day = day,
            startTime = startTime,
            endTime = endTime,
        )
}
