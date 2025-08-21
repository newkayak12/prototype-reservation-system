package com.reservation.menu

import com.reservation.menu.snapshot.MenuSnapshot
import com.reservation.menu.vo.MenuAttributes
import com.reservation.menu.vo.MenuDescription
import com.reservation.menu.vo.MenuPhoto
import com.reservation.menu.vo.MenuPrice

class Menu(
    private val id: String? = null,
    private val restaurantId: String,
    private val information: MenuDescription,
    private val photos: List<MenuPhoto> = listOf(),
    private val attributes: MenuAttributes,
    private val price: MenuPrice,
) {
    fun snapshot() =
        MenuSnapshot(
            id = id,
            restaurantId = restaurantId,
            title = information.title,
            description = information.description,
            price = price.amount,
            isRepresentative = attributes.isRepresentative,
            isRecommended = attributes.isRecommended,
            isVisible = attributes.isVisible,
            photoUrl = photos.map { it.url },
        )
}
