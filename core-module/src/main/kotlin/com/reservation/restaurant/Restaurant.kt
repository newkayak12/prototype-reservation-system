package com.reservation.restaurant

import com.reservation.restaurant.snapshot.RestaurantSnapshot
import com.reservation.restaurant.vo.RestaurantAddress
import com.reservation.restaurant.vo.RestaurantContact
import com.reservation.restaurant.vo.RestaurantCuisines
import com.reservation.restaurant.vo.RestaurantDescription
import com.reservation.restaurant.vo.RestaurantNationalities
import com.reservation.restaurant.vo.RestaurantPhotoBook
import com.reservation.restaurant.vo.RestaurantRoutine
import com.reservation.restaurant.vo.RestaurantTags

class Restaurant(
    val id: String,
    val companyId: String,
    private var introduce: RestaurantDescription,
    private var contact: RestaurantContact,
    private var address: RestaurantAddress,
) {
    private val routine = RestaurantRoutine()
    private val photos = RestaurantPhotoBook()
    private val tags = RestaurantTags()
    private val nationalities = RestaurantNationalities()
    private val cuisines = RestaurantCuisines()

    fun updateDescription(newDescription: RestaurantDescription) {
        introduce = newDescription
    }

    fun updateLocation(newLocation: RestaurantAddress) {
        address = newLocation
    }

    fun updateContract(newContract: RestaurantContact) {
        contact = newContract
    }

    fun manipulateRoutine(block: (RestaurantRoutine) -> Unit) = routine.apply(block)

    fun manipulatePhoto(block: (RestaurantPhotoBook) -> Unit) = photos.apply(block)

    fun manipulateTags(block: (RestaurantTags) -> Unit) = tags.apply(block)

    fun manipulateNationalities(block: (RestaurantNationalities) -> Unit) =
        nationalities.apply(block)

    fun manipulateCuisines(block: (RestaurantCuisines) -> Unit) = cuisines.apply(block)

    fun snapshot() =
        RestaurantSnapshot(
            id = id,
            companyId = companyId,
            name = introduce.name,
            introduce = introduce.introduce,
            phone = contact.phone,
            zipCode = address.zipCode,
            address = address.address,
            detail = address.detail,
            latitude = address.coordinateX,
            longitude = address.coordinateY,
            workingDays = routine.allWorkingDays(),
            photos = photos.allPhotos(),
            tags = tags.allTags(),
            nationalities = nationalities.allNationalities(),
            cuisines = cuisines.allCuisines(),
        )
}
