package com.reservation.rest.restaurant.request

import com.reservation.restaurant.policy.format.RestaurantWorkingDayForm
import com.reservation.restaurant.port.input.CreateRestaurantCommand.CreateProductCommandDto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

data class CreateRestaurantRequest(
    @NotNull
    val companyId: String,
    @NotBlank
    val name: String,
    @Size(max = 6000)
    val introduce: String?,
    @Size(min = 11, max = 13)
    val phone: String,
    @NotBlank
    val zipCode: String,
    @NotBlank
    @Size(max = 256)
    val address: String,
    @Size(max = 256)
    val detail: String,
    @NotNull
    val latitude: BigDecimal,
    @NotNull
    val longitude: BigDecimal,
    val workingDays: List<CreateRestaurantWorkingDay>,
    val photos: List<String>,
    val tags: List<Long>,
    val nationalities: List<Long>,
    val cuisines: List<Long>,
) {
    data class CreateRestaurantWorkingDay(
        @NotNull
        val day: DayOfWeek,
        @NotNull
        val startTime: LocalTime,
        @NotNull
        val endTime: LocalTime,
    ) {
        fun toCommand(): RestaurantWorkingDayForm =
            RestaurantWorkingDayForm(day, startTime, endTime)
    }

    fun toCommand(userId: String): CreateProductCommandDto =
        CreateProductCommandDto(
            companyId = companyId,
            userId = userId,
            name = name,
            introduce = introduce ?: "",
            phone = phone,
            zipCode = zipCode,
            address = address,
            detail = detail,
            latitude = latitude,
            longitude = longitude,
            workingDays = workingDays.map { it.toCommand() },
            photos = photos,
            tags = tags,
            nationalities = nationalities,
            cuisines = cuisines,
        )
}
