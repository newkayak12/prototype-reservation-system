package com.reservation.menu.policy.format

import java.math.BigDecimal

data class UpdateMenuForm(
    val id: String,
    val restaurantId: String,
    val title: String,
    val description: String,
    val photoUrl: List<String> = listOf(),
    val isRepresentative: Boolean = false,
    val isRecommended: Boolean = false,
    val isVisible: Boolean = false,
    val price: BigDecimal,
)
