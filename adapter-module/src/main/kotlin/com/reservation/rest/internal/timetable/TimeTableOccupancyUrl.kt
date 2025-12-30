package com.reservation.rest.internal.timetable

import com.reservation.rest.internal.InternalUrl

object TimeTableOccupancyUrl {
    private const val FIND_INTERNAL_PAYLOAD =
        "/timetable/{timeTableId:[0-9a-fA-F\\-]{36}}/occupancy/{timeTableOccupancyId:[0-9a-fA-F\\-]{36}}"
    const val FIND_INTERNAL = "${InternalUrl.PREFIX}${FIND_INTERNAL_PAYLOAD}"
}
