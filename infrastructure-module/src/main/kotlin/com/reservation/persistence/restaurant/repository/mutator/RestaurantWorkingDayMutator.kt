package com.reservation.persistence.restaurant.repository.mutator

import com.reservation.persistence.restaurant.entity.RestaurantEntity
import com.reservation.persistence.restaurant.entity.RestaurantWorkingDayEntity
import com.reservation.persistence.restaurant.entity.RestaurantWorkingDayId
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

    private fun RestaurantEntity.removeExceptedWorkingDays(day: List<DayOfWeek>) {
        val items = workingDaysAll().filter { !day.contains(it.id.day) }
        if (items.isEmpty()) {
            return
        }

        adjustWorkingDay {
            removeAll(items)
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

    private fun RestaurantEntity.addWorkingDays(schedule: List<WorkingDayMutatorForm>) {
        val exists =
            workingDaysAll().filter {
                schedule.map { schedule -> schedule.day }.contains(it.id.day)
            }
                .map { it.id.day }

        val items = schedule.filter { timetable -> !exists.contains(timetable.day) }

        if (items.isEmpty()) {
            return
        }

        adjustWorkingDay {
            for (element in items) {
                val entity =
                    RestaurantWorkingDayEntity(
                        RestaurantWorkingDayId(this@addWorkingDays.identifier, element.day),
                        this@addWorkingDays,
                        element.startTime,
                        element.endTime,
                    )

                add(entity)
            }
        }
    }

    data class WorkingDayMutatorForm(
        val day: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
    )

    fun appendWorkingDays(
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

    fun adjustWorkingDays(
        restaurantEntity: RestaurantEntity,
        ids: List<WorkingDayMutatorForm>,
    ) {
        restaurantEntity.removeExceptedWorkingDays(ids.map { it.day })
        restaurantEntity.addWorkingDays(ids)
    }
}
