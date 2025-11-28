package com.reservation.rest.timetable

object TimeTableOccupyUrl {
    private const val PREFIX = "/api/v1/time-table/booking"
    const val BOOKING = "$PREFIX/{restaurantId:[0-9a-fA-F\\-]{36}}"
}
