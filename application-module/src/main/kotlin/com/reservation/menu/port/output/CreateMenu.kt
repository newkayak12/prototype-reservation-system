package com.reservation.menu.port.output

import java.math.BigDecimal

interface CreateMenu {
    fun command(inquiry: CreateMenuInquiry): Boolean

    data class CreateMenuInquiry(
        val id: String? = null,
        val restaurantId: String,
        val title: String,
        val description: String,
        val price: BigDecimal,
        val isRepresentative: Boolean = false,
        val isRecommended: Boolean = false,
        val isVisible: Boolean = false,
        val photoUrl: List<String> = listOf(),
    )
}
