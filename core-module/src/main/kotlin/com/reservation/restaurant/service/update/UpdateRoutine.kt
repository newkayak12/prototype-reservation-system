package com.reservation.restaurant.service.update

import com.reservation.restaurant.Restaurant
import com.reservation.restaurant.policy.format.RestaurantWorkingDayForm
import com.reservation.restaurant.vo.RestaurantWorkingDay

class UpdateRoutine {
    fun adjust(
        restaurant: Restaurant,
        workingDays: List<RestaurantWorkingDayForm>,
    ) = restaurant.manipulateRoutine {
        for (item in it.allWorkingDays()) {
            it.remove(item.day)
        }

        for (item in workingDays) {
            it.add(RestaurantWorkingDay(item.day, item.startTime, item.endTime))
        }
    }
}
