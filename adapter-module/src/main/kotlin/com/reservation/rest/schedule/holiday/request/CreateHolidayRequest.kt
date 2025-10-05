package com.reservation.rest.schedule.holiday.request

import org.jetbrains.annotations.NotNull
import java.time.LocalDate

data class CreateHolidayRequest(
    @field:NotNull
    val date: LocalDate,
)
