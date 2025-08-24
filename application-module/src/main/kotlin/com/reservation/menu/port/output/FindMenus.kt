package com.reservation.menu.port.output

import com.reservation.menu.port.input.response.FindMenusQueryResult
import com.reservation.menu.port.input.response.FindMenusQueryResult.FindMenusPhotoQueryResult
import java.math.BigDecimal

interface FindMenus {
    fun query(restaurantId: String): List<FindMenusResult>

    data class FindMenusResult(
        val id: String,
        val restaurantId: String,
        val title: String,
        val description: String,
        val price: BigDecimal,
        val isRepresentative: Boolean,
        val isRecommended: Boolean,
        val isVisible: Boolean,
        val photos: List<FindMenusPhotoResult>,
    ) {
        fun toQuery(): FindMenusQueryResult =
            FindMenusQueryResult(
                id = id,
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

    data class FindMenusPhotoResult(
        val id: String,
        val url: String,
    ) {
        fun toQuery(): FindMenusPhotoQueryResult = FindMenusPhotoQueryResult(id, url)
    }
}
