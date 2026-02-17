package com.reservation.reservation.port.output

interface IsReservationExists {
    fun query(inquiry: IsReservationExistsInquiry): Boolean

    data class IsReservationExistsInquiry(
        val timeTableId: String,
        val timeTableOccupancyId: String,
    )
}
