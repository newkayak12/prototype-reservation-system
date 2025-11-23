package com.reservation.timetable.policy.exceptions

import com.reservation.exceptions.ClientException

class InvalidTimeTableUserIdException(
    override val message: String = "Invalid timeTable Id",
) : ClientException(message)
