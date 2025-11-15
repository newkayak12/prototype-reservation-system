package com.reservation.schedule.service

import com.reservation.schedule.Schedule
import com.reservation.schedule.TimeSpan
import com.reservation.schedule.exceptions.InvalidTimeSpanElementException
import com.reservation.schedule.policy.form.CreateTimeSpanForm
import com.reservation.schedule.policy.validation.TimeSpanRestaurantIdEmptyValidationPolicy
import com.reservation.schedule.policy.validation.TimeSpanRestaurantIdFormatValidationPolicy
import com.reservation.schedule.policy.validation.TimeSpanRestaurantIdPolicy
import com.reservation.schedule.policy.validation.TimeSpanTimePolicy
import com.reservation.schedule.policy.validation.TimeSpanTimeSequenceValidationPolicy
import com.reservation.schedule.snapshot.ScheduleSnapshot
import java.time.LocalTime

class CreateTimeSpanDomainService {
    private val restaurantIdPolicies: List<TimeSpanRestaurantIdPolicy> =
        listOf(
            TimeSpanRestaurantIdEmptyValidationPolicy(),
            TimeSpanRestaurantIdFormatValidationPolicy(),
        )

    private val restaurantTimeSpanPolicies: List<TimeSpanTimePolicy> =
        listOf(TimeSpanTimeSequenceValidationPolicy())

    private fun <T : TimeSpanRestaurantIdPolicy> List<T>.validatePolicies(target: String) =
        firstOrNull { !it.validate(target) }
            ?.let {
                throw InvalidTimeSpanElementException(it.reason)
            }

    private fun <T : TimeSpanTimePolicy> List<T>.validatePolicies(
        startTime: LocalTime,
        endTime: LocalTime,
    ) = firstOrNull { !it.validate(startTime, endTime) }
        ?.let {
            throw InvalidTimeSpanElementException(it.reason)
        }

    private fun validateRestaurantId(restaurantId: String) =
        restaurantIdPolicies.validatePolicies(restaurantId)

    private fun validateTime(
        startTime: LocalTime,
        endTime: LocalTime,
    ) = restaurantTimeSpanPolicies.validatePolicies(startTime, endTime)

    private fun validate(form: CreateTimeSpanForm) {
        validateRestaurantId(form.restaurantId)
        validateTime(form.startTime, form.endTime)
    }

    fun create(
        schedule: Schedule,
        form: CreateTimeSpanForm,
    ): ScheduleSnapshot {
        validate(form)

        val timeSpan =
            TimeSpan(
                restaurantId = form.restaurantId,
                day = form.day,
                startTime = form.startTime,
                endTime = form.endTime,
            )
        schedule.addTimeSpan(timeSpan)

        return schedule.snapshot()
    }
}
