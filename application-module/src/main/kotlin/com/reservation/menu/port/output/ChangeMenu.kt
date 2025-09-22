package com.reservation.menu.port.output

import java.math.BigDecimal

interface ChangeMenu {
    fun command(inquiry: UpdateMenuInquiry): Boolean

    data class UpdateMenuInquiry(
        val id: String,
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
