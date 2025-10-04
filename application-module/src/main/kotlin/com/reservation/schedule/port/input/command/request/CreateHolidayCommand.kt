package com.reservation.schedule.port.input.command.request

import java.time.LocalDate

data class CreateHolidayCommand(
    val restaurantId: String,
    val date: LocalDate,
)
