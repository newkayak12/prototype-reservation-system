package com.reservation.schedule

import com.reservation.schedule.snapshot.HolidaySnapshot
import java.time.LocalDate

class Holiday(
    private val id: String? = null,
    private val restaurantId: String,
    private val date: LocalDate,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Holiday

        if (id != other.id) return false
        if (restaurantId != other.restaurantId) return false
        if (date != other.date) return false

        return true
    }

    override fun hashCode(): Int {
        var result = restaurantId.hashCode()
        result = 31 * result + date.hashCode()
        return result
    }

    fun snapshot() =
        HolidaySnapshot(
            id = id,
            restaurantId = restaurantId,
            date = date,
        )
}
