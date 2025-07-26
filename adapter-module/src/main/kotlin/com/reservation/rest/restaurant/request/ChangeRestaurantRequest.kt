package com.reservation.rest.restaurant.request

import com.reservation.restaurant.port.input.UpdateRestaurantCommand.ChangeRestaurantCommandDto
import com.reservation.restaurant.port.input.UpdateRestaurantCommand.ChangeWorkingDayCommandDto
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.Length
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

data class ChangeRestaurantRequest(
    @field:NotBlank
    val name: String,
    @field:Length(max = 6000)
    val introduce: String,
    @field:Length(min = 11, max = 13)
    val phone: String,
    @field:NotBlank
    val zipCode: String,
    @field:NotBlank
    @field:Length(max = 256)
    val address: String,
    @field:Length(max = 256)
    val detail: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val workingDays: List<ChangeWorkingDayRequest> = listOf(),
    val tags: List<Long> = listOf(),
    val nationalities: List<Long> = listOf(),
    val cuisines: List<Long> = listOf(),
) {
    data class ChangeWorkingDayRequest(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    fun toCommand(
        id: String,
        userId: String,
        photos: List<MultipartFile>,
    ) = ChangeRestaurantCommandDto(
        id,
        userId,
        name,
        introduce,
        phone,
        zipCode,
        address,
        detail = "",
        latitude,
        longitude,
        workingDays.map { ChangeWorkingDayCommandDto(it.day, it.startTime, it.endTime) },
        photos,
        tags,
        nationalities,
        cuisines,
    )
}
