package com.reservation.restaurant.policy.format

import java.math.BigDecimal

data class ChangeRestaurantForm(
    val id: String,
    val name: String,
    val introduce: String,
    val phone: String,
    val zipCode: String,
    val address: String,
    val detail: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val workingDays: List<RestaurantWorkingDayForm> = listOf(),
    val photos: List<String> = listOf(),
    val tags: List<Long> = listOf(),
    val nationalities: List<Long> = listOf(),
    val cuisines: List<Long> = listOf(),
)
