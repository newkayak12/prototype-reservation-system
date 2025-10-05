package com.reservation.schedule.service

import com.reservation.schedule.Holiday
import com.reservation.schedule.Schedule
import com.reservation.schedule.exceptions.InvalidateHolidayElementException
import com.reservation.schedule.policy.form.CreateHolidayForm
import com.reservation.schedule.policy.validation.HolidayDateMustNotBePassedValidationPolicy
import com.reservation.schedule.policy.validation.HolidayDatePolicy
import com.reservation.schedule.policy.validation.HolidayRestaurantIdEmptyValidationPolicy
import com.reservation.schedule.policy.validation.HolidayRestaurantIdFormatValidationPolicy
import com.reservation.schedule.policy.validation.HolidayRestaurantIdPolicy
import com.reservation.schedule.snapshot.ScheduleSnapshot
import java.time.LocalDate

class CreateHolidayDomainService {
    private val restaurantIdPolicy: List<HolidayRestaurantIdPolicy> =
        listOf(
            HolidayRestaurantIdEmptyValidationPolicy(),
            HolidayRestaurantIdFormatValidationPolicy(),
        )
    private val datePolicy: List<HolidayDatePolicy> =
        listOf(HolidayDateMustNotBePassedValidationPolicy())

    private fun <T : HolidayRestaurantIdPolicy> List<T>.validatePolicies(target: String) =
        firstOrNull { !it.validate(target) }
            ?.let {
                throw InvalidateHolidayElementException(it.reason)
            }

    private fun <T : HolidayDatePolicy> List<T>.validatePolicies(target: LocalDate) =
        firstOrNull { !it.validate(target) }
            ?.let {
                throw InvalidateHolidayElementException(it.reason)
            }

    private fun validate(form: CreateHolidayForm) {
        validateRestaurantId(form.restaurantId)
        validateDate(form.date)
    }

    private fun validateRestaurantId(restaurantId: String) =
        restaurantIdPolicy.validatePolicies(restaurantId)

    private fun validateDate(date: LocalDate) = datePolicy.validatePolicies(date)

    fun create(
        schedule: Schedule,
        form: CreateHolidayForm,
    ): ScheduleSnapshot {
        validate(form)

        val holiday = Holiday(restaurantId = form.restaurantId, date = form.date)
        schedule.addHoliday(holiday)

        return schedule.snapshot()
    }
}
