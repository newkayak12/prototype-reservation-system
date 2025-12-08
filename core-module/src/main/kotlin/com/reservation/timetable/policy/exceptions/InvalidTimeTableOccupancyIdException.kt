package com.reservation.timetable.policy.exceptions

import com.reservation.exceptions.ClientException

class InvalidTimeTableOccupancyIdException(
    override val message: String = "Invalid time table occupancy id",
) : ClientException(message)
