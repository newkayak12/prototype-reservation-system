package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.RestaurantEntity
import com.reservation.persistence.restaurant.RestaurantWorkingDayEntity
import com.reservation.persistence.restaurant.RestaurantWorkingDayId
import java.time.DayOfWeek
import java.time.LocalTime

object RestaurantWorkingDayMutator {
    private fun RestaurantEntity.removeWorkingDay(day: DayOfWeek) {
        val item = workingDaysAll().find { it.id.day == day }
        if (item == null) {
            return
        }
        adjustWorkingDay {
            remove(item)
        }
    }

    private fun RestaurantEntity.addWorkingDay(
        day: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime,
    ) {
        val item = workingDaysAll().find { it.id.day == day }
        if (item != null) {
            return
        }

        adjustWorkingDay {
            add(
                RestaurantWorkingDayEntity(
                    id = RestaurantWorkingDayId(this@addWorkingDay.identifier, day),
                    restaurant = this@addWorkingDay,
                    startTime = startTime,
                    endTime = endTime,
                ),
            )
        }
    }

    data class WorkingDayMutatorForm(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    fun addWorkingDays(
        restaurantEntity: RestaurantEntity,
        workingDays: List<WorkingDayMutatorForm>,
    ) = workingDays.forEach {
        restaurantEntity.addWorkingDay(
            it.day,
            it.startTime,
            it.endTime,
        )
    }

    fun purgeWorkingDays(restaurantEntity: RestaurantEntity) {
        restaurantEntity.adjustWorkingDay { removeAll(restaurantEntity.workingDaysAll()) }
    }
}
