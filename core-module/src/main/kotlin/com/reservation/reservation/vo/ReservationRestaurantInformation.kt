package com.reservation.reservation.vo

import com.reservation.reservation.snapshot.vo.ReservationSnapshotRestaurantInformation

class ReservationRestaurantInformation(
    val restaurantId: String,
    val tableNumber: Int,
    val tableSize: Int,
) {
    fun toSnapshot() =
        ReservationSnapshotRestaurantInformation(
            restaurantId = restaurantId,
            tableNumber = tableNumber,
            tableSize = tableSize,
        )
}
