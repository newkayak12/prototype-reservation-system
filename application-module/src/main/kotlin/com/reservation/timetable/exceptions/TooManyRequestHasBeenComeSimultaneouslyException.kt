package com.reservation.timetable.exceptions

import com.reservation.exceptions.ClientException

class TooManyRequestHasBeenComeSimultaneouslyException(
    message: String = "too many request has been come simultaneously.",
) : ClientException(message)
