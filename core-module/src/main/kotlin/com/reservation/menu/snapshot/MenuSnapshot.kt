package com.reservation.menu.snapshot

import java.math.BigDecimal

data class MenuSnapshot(
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
