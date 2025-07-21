package com.reservation.restaurant.service.update

import com.reservation.restaurant.Restaurant
import com.reservation.restaurant.vo.RestaurantPhoto

class UpdatePhoto {
    fun adjust(
        restaurant: Restaurant,
        photos: List<String>,
    ) = restaurant.manipulatePhoto {
        for (item in it.allPhotos()) {
            it.remove(item)
        }

        for (item in photos) {
            it.add(RestaurantPhoto(item))
        }
    }
}
