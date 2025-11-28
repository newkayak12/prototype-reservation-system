package com.reservation.timetable.exception

import com.reservation.exceptions.ClientException

class AllTheThingsAreAlreadyOccupiedException(
    message: String = "All the things are already occupied.",
) : ClientException(message)
