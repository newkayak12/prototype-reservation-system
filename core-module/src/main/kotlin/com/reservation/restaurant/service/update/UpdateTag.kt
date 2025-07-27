package com.reservation.restaurant.service.update

import com.reservation.restaurant.Restaurant

class UpdateTag {
    fun adjust(
        restaurant: Restaurant,
        tags: List<Long>,
    ) = restaurant.manipulateTags {
        for (item in it.allTags()) {
            it.remove(item)
        }

        for (item in tags) {
            it.add(item)
        }
    }
}
