package com.reservation.timetable.exception

import com.reservation.exceptions.ClientException

class TooManyCreateTimeTableOccupancyRequestException : ClientException(
    message = "Too many request",
)
