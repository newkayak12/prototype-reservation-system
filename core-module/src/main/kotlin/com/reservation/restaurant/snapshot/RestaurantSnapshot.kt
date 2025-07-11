package com.reservation.restaurant.snapshot

import com.reservation.restaurant.vo.RestaurantPhoto
import com.reservation.restaurant.vo.RestaurantWorkingDay
import java.math.BigDecimal

data class RestaurantSnapshot(
    val id: String? = null,
    val companyId: String,
    val name: String,
    val introduce: String,
    val phone: String,
    val zipCode: String,
    val address: String,
    val detail: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val workingDays: List<RestaurantWorkingDay>,
    val photos: List<RestaurantPhoto>,
    val tags: List<Long>,
    val nationalities: List<Long>,
    val cuisines: List<Long>,
)
