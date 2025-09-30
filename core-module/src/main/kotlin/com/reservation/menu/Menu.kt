package com.reservation.menu

import com.reservation.menu.snapshot.MenuSnapshot
import com.reservation.menu.vo.MenuAttributes
import com.reservation.menu.vo.MenuDescription
import com.reservation.menu.vo.MenuPhotoBook
import com.reservation.menu.vo.MenuPrice

class Menu(
    private val id: String? = null,
    private var restaurantId: String,
    private var information: MenuDescription,
    private val menuPhotoBook: MenuPhotoBook,
    private var attributes: MenuAttributes,
    private var price: MenuPrice,
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
            photoUrl = menuPhotoBook.allPhotos().map { it.url },
        )

    fun changeInformation(information: MenuDescription) {
        this.information = information
    }

    fun changeAttributes(attributes: MenuAttributes) {
        this.attributes = attributes
    }

    fun changePrice(price: MenuPrice) {
        this.price = price
    }

    fun manipulatePhoto(block: (MenuPhotoBook) -> Unit) {
        menuPhotoBook.apply(block)
    }
}
