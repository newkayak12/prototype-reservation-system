package com.reservation.reservation

import com.reservation.enumeration.ReservationStatus
import com.reservation.enumeration.ReservationStatus.RESERVED
import com.reservation.reservation.snapshot.ReservationSnapshot
import com.reservation.reservation.vo.ReservationBooker
import com.reservation.reservation.vo.ReservationOccupancy
import com.reservation.reservation.vo.ReservationRestaurantInformation
import com.reservation.reservation.vo.ReservationSchedule

class Reservation(
    val id: String? = null,
    val booker: ReservationBooker,
    val restaurantInformation: ReservationRestaurantInformation,
    val schedule: ReservationSchedule,
    val occupancy: ReservationOccupancy,
    val reservationStatus: ReservationStatus = RESERVED,
) {
    fun toSnapshot() =
        ReservationSnapshot(
            id = id,
            booker = booker.toSnapshot(),
            restaurantInformation = restaurantInformation.toSnapshot(),
            schedule = schedule.toSnapshot(),
            occupancy = occupancy.toSnapshot(),
            reservationStatus = reservationStatus,
        )
}
