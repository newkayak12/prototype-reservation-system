package com.reservation.schedule

import com.reservation.schedule.snapshot.TableSnapshot

class Table(
    private val id: String? = null,
    private val restaurantId: String,
    private val tableNumber: Int,
    private val tableSize: Int,
) {
    fun snapshot() =
        TableSnapshot(
            id = id,
            restaurantId = restaurantId,
            tableNumber = tableNumber,
            tableSize = tableSize,
        )
}
