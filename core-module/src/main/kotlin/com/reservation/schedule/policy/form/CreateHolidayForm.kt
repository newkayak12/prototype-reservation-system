package com.reservation.schedule.policy.form

import java.time.LocalDate

data class CreateHolidayForm(
    val restaurantId: String,
    val date: LocalDate,
)
