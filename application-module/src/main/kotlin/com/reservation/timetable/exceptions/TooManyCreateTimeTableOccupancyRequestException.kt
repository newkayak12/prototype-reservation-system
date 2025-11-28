package com.reservation.timetable.exceptions

import com.reservation.exceptions.ClientException

class TooManyCreateTimeTableOccupancyRequestException : ClientException(
    message = "Too many request",
)
