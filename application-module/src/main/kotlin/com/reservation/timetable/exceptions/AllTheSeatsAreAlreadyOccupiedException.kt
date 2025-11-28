package com.reservation.timetable.exceptions

import com.reservation.exceptions.ClientException

class AllTheSeatsAreAlreadyOccupiedException(
    message: String = "All the seats are already occupied.",
) : ClientException(message)
