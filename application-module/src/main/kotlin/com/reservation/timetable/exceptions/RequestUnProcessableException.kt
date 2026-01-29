package com.reservation.timetable.exceptions

import com.reservation.exceptions.ClientException

class RequestUnProcessableException(
    message: String = "All the things are already occupied.",
) : ClientException(message)
