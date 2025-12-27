package com.reservation.reservation.service

import com.reservation.reservation.Reservation
import com.reservation.reservation.policy.format.CreateReservationForm
import com.reservation.reservation.service.validate.ValidateRestaurantId
import com.reservation.reservation.service.validate.ValidateTimeTableId
import com.reservation.reservation.service.validate.ValidateTimeTableIdOccupancy
import com.reservation.reservation.service.validate.ValidateUserId
import com.reservation.reservation.snapshot.ReservationSnapshot
import com.reservation.reservation.vo.ReservationBooker
import com.reservation.reservation.vo.ReservationOccupancy
import com.reservation.reservation.vo.ReservationRestaurantInformation
import com.reservation.reservation.vo.ReservationSchedule

class CreateReservationDomainService {
    private val validateUserId = ValidateUserId()
    private val validateRestaurantId = ValidateRestaurantId()
    private val validateTimeTableId = ValidateTimeTableId()
    private val validateTimeTableIdOccupancy = ValidateTimeTableIdOccupancy()

    private fun validate(form: CreateReservationForm) {
        validateUserId.validate(form.userId)
        validateRestaurantId.validate(form.restaurantId)
        validateTimeTableId.validate(form.timeTableId)
        validateTimeTableIdOccupancy.validate(form.timeTableOccupancyId)
    }

    fun createReservation(form: CreateReservationForm): ReservationSnapshot {
        validate(form)

        return Reservation(
            booker = ReservationBooker(form.userId),
            restaurantInformation =
                ReservationRestaurantInformation(
                    restaurantId = form.restaurantId,
                    tableNumber = form.tableNumber,
                    tableSize = form.tableSize,
                ),
            schedule =
                ReservationSchedule(
                    timeTableId = form.timeTableId,
                    date = form.date,
                    day = form.day,
                    startTime = form.startTime,
                    endTime = form.endTime,
                ),
            occupancy =
                ReservationOccupancy(
                    timeTableOccupancyId = form.timeTableOccupancyId,
                    occupiedDatetime = form.occupiedDatetime,
                ),
        )
            .toSnapshot()
    }
}
