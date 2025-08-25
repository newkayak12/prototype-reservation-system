package com.reservation.rest.menu.response

import com.reservation.menu.port.input.response.FindMenusQueryResult
import com.reservation.menu.port.input.response.FindMenusQueryResult.FindMenusPhotoQueryResult
import java.math.BigDecimal

data class FindMenuResponse(
    val id: String,
    val restaurantId: String,
    val title: String,
    val description: String,
    val price: BigDecimal,
    val isRepresentative: Boolean,
    val isRecommended: Boolean,
    val isVisible: Boolean,
    val photos: List<FindMenusPhotoResponse>,
) {
    data class FindMenusPhotoResponse(
        val id: String,
        val url: String,
    ) {
        companion object {
            fun from(element: FindMenusPhotoQueryResult) =
                FindMenusPhotoResponse(element.id, element.url)
        }
    }

    companion object {
        fun from(element: FindMenusQueryResult) =
            FindMenuResponse(
                id = element.id,
                restaurantId = element.restaurantId,
                title = element.title,
                description = element.description,
                price = element.price,
                isRepresentative = element.isRepresentative,
                isRecommended = element.isRecommended,
                isVisible = element.isVisible,
                photos = element.photos.map { FindMenusPhotoResponse.from(it) },
            )
    }
}
