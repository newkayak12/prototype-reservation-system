package com.reservation.schedule.service

import com.reservation.schedule.Schedule
import com.reservation.schedule.exceptions.InvalidateScheduleElementException
import com.reservation.schedule.policy.validation.ScheduleRestaurantIdEmptyValidationPolicy
import com.reservation.schedule.policy.validation.ScheduleRestaurantIdFormatValidationPolicy
import com.reservation.schedule.policy.validation.ScheduleRestaurantIdPolicy
import com.reservation.schedule.snapshot.ScheduleSnapshot

class CreateScheduleDomainService {
    private val restaurantIdPolicies: List<ScheduleRestaurantIdPolicy> =
        listOf(
            ScheduleRestaurantIdEmptyValidationPolicy(),
            ScheduleRestaurantIdFormatValidationPolicy()
        )

    private fun <T : ScheduleRestaurantIdPolicy> List<T>.validatePolicies(
        target: String,
    ) = firstOrNull { !it.validate(target) }
        ?.let {
            throw InvalidateScheduleElementException(it.reason)
        }


    private fun validateRestaurantId(restaurantId: String) =
        restaurantIdPolicies.validatePolicies(restaurantId)


    fun create(restaurantId: String): ScheduleSnapshot {
        validateRestaurantId(restaurantId)

        val schedule = Schedule(restaurantId)

        return schedule.snapshot()
    }
}
