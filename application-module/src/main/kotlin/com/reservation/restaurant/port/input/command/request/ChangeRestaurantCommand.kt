package com.reservation.restaurant.port.input.command.request

import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

data class ChangeRestaurantCommand(
    val id: String,
    val userId: String,
    val name: String,
    val introduce: String,
    val phone: String,
    val zipCode: String,
    val address: String,
    val detail: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val workingDays: List<ChangeWorkingDayCommand> = listOf(),
    val photos: List<MultipartFile> = listOf(),
    val tags: List<Long> = listOf(),
    val nationalities: List<Long> = listOf(),
    val cuisines: List<Long> = listOf(),
) {
    data class ChangeWorkingDayCommand(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )
}
