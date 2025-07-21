package com.reservation.restaurant.service.update

import com.reservation.restaurant.Restaurant

class UpdateCuisines {
    fun adjust(
        restaurant: Restaurant,
        cuisines: List<Long>,
    ) = restaurant.manipulateCuisines {
        for (item in it.allCuisines()) {
            it.remove(item)
        }

        for (item in cuisines) {
            it.add(item)
        }
    }
}
