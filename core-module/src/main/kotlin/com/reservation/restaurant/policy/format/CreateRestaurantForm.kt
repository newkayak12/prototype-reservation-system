package com.reservation.restaurant.policy.format

import java.math.BigDecimal

data class CreateRestaurantForm(
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
