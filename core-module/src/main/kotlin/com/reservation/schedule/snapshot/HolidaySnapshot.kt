package com.reservation.schedule.snapshot

import java.time.LocalDate

class HolidaySnapshot(
    private val id: String? = null,
    private val restaurantId: String,
    private val date: LocalDate
) {
}
