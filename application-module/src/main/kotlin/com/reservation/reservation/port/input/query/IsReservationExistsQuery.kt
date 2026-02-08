package com.reservation.reservation.port.input.query

data class IsReservationExistsQuery(
    val timeTableId: String,
    val timeTableOccupancyId: String,
)
