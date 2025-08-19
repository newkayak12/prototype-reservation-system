package com.reservation.menu.policy.format

import java.math.BigDecimal

data class CreateMenuForm(
    val restaurantId: String,
    val title: String,
    val description: String,
    val price: BigDecimal,
    val isRepresentative: Boolean = false,
    val isRecommended: Boolean = false,
    val isVisible: Boolean = false,
    val photoUrl: List<String> = listOf(),
)
