package com.reservation.menu.port.output

import com.reservation.menu.port.input.response.FindMenuQueryResult
import com.reservation.menu.port.input.response.FindMenuQueryResult.FindMenusPhotoQueryResult
import java.math.BigDecimal

interface FindMenu {
    fun query(restaurantId: String): FindMenuResult?

    data class FindMenuResult(
        val identifier: String,
        val restaurantId: String,
        val title: String,
        val description: String,
        val price: BigDecimal,
        val isRepresentative: Boolean,
        val isRecommended: Boolean,
        val isVisible: Boolean,
        val photos: MutableList<FindMenuPhotoResult> = mutableListOf(),
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

        fun bind(incoming: List<FindMenuPhotoResult>) {
            this.photos.addAll(incoming)
        }
    }

    data class FindMenuPhotoResult(
        val id: String,
        val url: String,
    ) {
        fun toQuery(): FindMenusPhotoQueryResult = FindMenusPhotoQueryResult(id, url)
    }
}
