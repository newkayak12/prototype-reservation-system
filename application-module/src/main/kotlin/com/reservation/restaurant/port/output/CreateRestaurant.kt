package com.reservation.restaurant.port.output

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime

interface CreateRestaurant {
    fun command(inquiry: CreateProductInquiry): String

    data class CreateProductInquiry(
        val companyId: String,
        val userId: String,
        val name: String,
        val introduce: String,
        val phone: String,
        val zipCode: String,
        val address: String,
        val detail: String,
        val latitude: BigDecimal,
        val longitude: BigDecimal,
        val workingDays: List<WorkingDay>,
        val photos: List<Photo>,
        val tags: List<Long>,
        val nationalities: List<Long>,
        val cuisines: List<Long>,
    )

    data class WorkingDay(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WorkingDay) return false

            if (day != other.day) return false

            return true
        }

        override fun hashCode(): Int {
            return day.hashCode()
        }
    }

    data class Photo(
        val url: String,
    )
}
