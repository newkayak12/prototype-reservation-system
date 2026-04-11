package com.reservation.httpinterface.exceptions

import com.reservation.exceptions.ClientException

class ResponseBodyRequiredException(message: String = "Body cannot be empty") : ClientException(
    message,
)
