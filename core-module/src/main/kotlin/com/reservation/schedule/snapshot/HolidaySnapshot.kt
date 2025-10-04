package com.reservation.schedule.snapshot

import java.time.LocalDate

class HolidaySnapshot(
    val id: String? = null,
    val restaurantId: String,
    val date: LocalDate,
)
