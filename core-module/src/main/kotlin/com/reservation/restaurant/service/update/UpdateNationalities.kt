package com.reservation.restaurant.service.update

import com.reservation.restaurant.Restaurant

class UpdateNationalities {
    fun adjust(
        restaurant: Restaurant,
        nationalities: List<Long>,
    ) = restaurant.manipulateNationalities {
        for (item in it.allNationalities()) {
            it.remove(item)
        }

        for (item in nationalities) {
            it.add(item)
        }
    }
}
