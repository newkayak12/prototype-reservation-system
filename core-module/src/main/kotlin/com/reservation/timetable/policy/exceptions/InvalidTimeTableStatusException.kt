package com.reservation.timetable.policy.exceptions

import com.reservation.exceptions.ClientException

class InvalidTimeTableStatusException(
    override val message: String = "Invalid time table status",
) : ClientException(message)
