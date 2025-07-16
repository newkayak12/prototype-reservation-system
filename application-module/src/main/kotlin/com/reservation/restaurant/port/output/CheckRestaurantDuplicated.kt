package com.reservation.restaurant.port.output

fun interface CheckRestaurantDuplicated {
    fun query(inquiry: CheckRestaurantDuplicatedInquiry): Boolean

    data class CheckRestaurantDuplicatedInquiry(
        val companyId: String,
        val name: String,
    )
}
