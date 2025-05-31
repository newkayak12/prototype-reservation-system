package com.reservation.exceptions

open class InvalidSituationException(
    message: String = "It's CRITICAL EXCEPTION",
) : RuntimeException(message)
