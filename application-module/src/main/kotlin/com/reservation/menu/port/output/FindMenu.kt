package com.reservation.menu.port.output

import com.reservation.menu.port.input.response.FindMenuQueryResult
import com.reservation.menu.port.input.response.FindMenuQueryResult.FindMenusPhotoQueryResult
import java.math.BigDecimal

interface FindMenu {
    fun query(menuId: String): FindMenuResult?

    data class FindMenuResult(
        val identifier: String,
        val restaurantId: String,
        val title: String,
        val description: String,
        val price: BigDecimal,
        val isRepresentative: Boolean,
        val isRecommended: Boolean,
        val isVisible: Boolean,
        val photos: List<FindMenuPhotoResult> = mutableListOf(),
    ) {
        fun toQuery(): FindMenuQueryResult =
            FindMenuQueryResult(
                id = identifier,
                restaurantId = restaurantId,
                title = title,
                description = description,
                price = price,
                isRepresentative = isRepresentative,
                isRecommended = isRecommended,
                isVisible = isVisible,
                photos = photos.map { it.toQuery() },
            )
    }

    data class FindMenuPhotoResult(
        val id: String,
        val url: String,
    ) {
        fun toQuery(): FindMenusPhotoQueryResult = FindMenusPhotoQueryResult(id, url)
    }
}
