package com.reservation.menu.port.output

import com.reservation.menu.port.input.response.FindMenusQueryResult
import com.reservation.menu.port.input.response.FindMenusQueryResult.FindMenusPhotoQueryResult
import java.math.BigDecimal

interface FindMenus {
    fun query(restaurantId: String): List<FindMenusResult>

    data class FindMenusResult(
        val identifier: String,
        val restaurantId: String,
        val title: String,
        val description: String,
        val price: BigDecimal,
        val isRepresentative: Boolean,
        val isRecommended: Boolean,
        val isVisible: Boolean,
        val photos: MutableList<FindMenusPhotoResult> = mutableListOf(),
    ) {
        fun toQuery(): FindMenusQueryResult =
            FindMenusQueryResult(
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

        fun bind(incoming: List<FindMenusPhotoResult>) {
            this.photos.addAll(incoming)
        }
    }

    data class FindMenusPhotoResult(
        val id: String,
        val url: String,
    ) {
        fun toQuery(): FindMenusPhotoQueryResult = FindMenusPhotoQueryResult(id, url)
    }
}
