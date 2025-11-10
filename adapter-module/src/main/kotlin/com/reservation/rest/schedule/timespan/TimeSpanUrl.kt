package com.reservation.rest.schedule.timespan

object TimeSpanUrl {
    private const val PREFIX = "/api/v1/time-span"
    const val CREATE = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"
}
