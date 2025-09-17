package com.reservation.menu.port.input.request

import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

data class UpdateMenuCommand(
    val id: String,
    val restaurantId: String,
    val title: String,
    val description: String,
    val price: BigDecimal,
    val isRepresentative: Boolean = false,
    val isRecommended: Boolean = false,
    val isVisible: Boolean = false,
    val photoUrl: List<UpdateMenuPreviousImage>,
    val photos: List<MultipartFile> = listOf(),
) {
    data class UpdateMenuPreviousImage(
        val id: String,
        val url: String,
    )
}
