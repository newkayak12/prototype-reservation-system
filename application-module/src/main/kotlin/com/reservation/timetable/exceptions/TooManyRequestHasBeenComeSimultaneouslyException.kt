package com.reservation.timetable.exception

import com.reservation.exceptions.ClientException

class TooManyRequestHasBeenComeSimultaneouslyException(
    message: String = "too many request has been come simultaneously.",
) : ClientException(message)
