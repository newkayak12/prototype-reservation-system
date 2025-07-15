package com.reservation.restaurant.port.input

import com.reservation.restaurant.policy.format.RestaurantWorkingDayForm
import java.math.BigDecimal

fun interface CreateRestaurantCommand {
    /**
     * 성공하면 true, 실패하면 false
     */
    fun execute(request: CreateProductCommandDto): Boolean

    data class CreateProductCommandDto(
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
        val workingDays: List<RestaurantWorkingDayForm>,
        val photos: List<String>,
        val tags: List<Long>,
        val nationalities: List<Long>,
        val cuisines: List<Long>,
    )
}
