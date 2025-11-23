package com.reservation.timetable.exception

import com.reservation.exceptions.ClientException

class AllTheSeatsAreAlreadyOccupiedException(
    message: String = "All the seats are already occupied.",
) : ClientException(message)
