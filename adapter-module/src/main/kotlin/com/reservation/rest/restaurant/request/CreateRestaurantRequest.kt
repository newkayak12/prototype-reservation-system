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
    @field:NotBlank
    val companyId: String,
    @field:NotBlank
    val name: String,
    @field:Size(max = 6000)
    val introduce: String?,
    @field:Size(min = 11, max = 13)
    val phone: String,
    @field:NotBlank
    val zipCode: String,
    @field:NotBlank
    @field:Size(max = 256)
    val address: String,
    @field:Size(max = 256)
    val detail: String = "",
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val workingDays: List<CreateRestaurantWorkingDay>,
    val photos: List<String>,
    val tags: List<Long>,
    val nationalities: List<Long>,
    val cuisines: List<Long>,
) {
    data class CreateRestaurantWorkingDay(
        @field:NotNull
        val day: DayOfWeek,
        @field:NotNull
        val startTime: LocalTime,
        @field:NotNull
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
