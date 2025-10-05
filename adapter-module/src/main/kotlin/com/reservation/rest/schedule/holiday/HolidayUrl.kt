package com.reservation.rest.schedule.holiday

object HolidayUrl {
    private const val PREFIX = "/api/v1/holiday"
    const val CREATE = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"
}
