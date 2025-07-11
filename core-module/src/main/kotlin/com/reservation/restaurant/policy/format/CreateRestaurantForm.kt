package com.reservation.restaurant.policy.format

import com.reservation.restaurant.vo.RestaurantPhoto
import java.math.BigDecimal

data class CreateRestaurantForm(
    val id: String,
    val companyId: String,
    val name: String,
    val introduce: String,
    val phone: String,
    val zipCode: String,
    val address: String,
    val detail: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val workingDays: List<RestaurantWorkingDayForm>,
    val photos: List<RestaurantPhoto>,
    val tags: List<Long>,
    val nationalities: List<Long>,
    val cuisines: List<Long>,
)
