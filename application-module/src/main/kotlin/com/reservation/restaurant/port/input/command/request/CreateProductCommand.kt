package com.reservation.restaurant.port.input.command.request

import com.reservation.restaurant.policy.format.RestaurantWorkingDayForm
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

data class CreateProductCommand(
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
    val photos: List<MultipartFile>,
    val tags: List<Long>,
    val nationalities: List<Long>,
    val cuisines: List<Long>,
)
