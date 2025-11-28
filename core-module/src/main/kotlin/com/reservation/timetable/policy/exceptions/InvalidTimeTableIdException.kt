package com.reservation.timetable.policy.exceptions

import com.reservation.exceptions.ClientException

class InvalidTimeTableIdException(
    override val message: String = "Invalid time table id",
) : ClientException(message)
