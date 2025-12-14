package com.reservation.reservation.snapshot

import com.reservation.enumeration.ReservationStatus
import com.reservation.enumeration.ReservationStatus.RESERVED
import com.reservation.reservation.snapshot.vo.ReservationSnapshotBooker
import com.reservation.reservation.snapshot.vo.ReservationSnapshotOccupancy
import com.reservation.reservation.snapshot.vo.ReservationSnapshotRestaurantInformation
import com.reservation.reservation.snapshot.vo.ReservationSnapshotSchedule

class ReservationSnapshot(
    val id: String? = null,
    val booker: ReservationSnapshotBooker,
    val restaurantInformation: ReservationSnapshotRestaurantInformation,
    val schedule: ReservationSnapshotSchedule,
    val occupancy: ReservationSnapshotOccupancy,
    val reservationStatus: ReservationStatus = RESERVED,
)
