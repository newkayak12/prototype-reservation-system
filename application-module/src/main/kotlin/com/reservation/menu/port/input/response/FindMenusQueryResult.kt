package com.reservation.menu.port.input.response

import java.math.BigDecimal

data class FindMenusQueryResult(
    val id: String,
    val restaurantId: String,
    val title: String,
    val description: String,
    val price: BigDecimal,
    val isRepresentative: Boolean,
    val isRecommended: Boolean,
    val isVisible: Boolean,
    val photos: List<FindMenusPhotoQueryResult>,
) {
    data class FindMenusPhotoQueryResult(
        val id: String,
        val url: String,
    )
}
