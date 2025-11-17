package com.reservation.exceptions

abstract class InvalidSituationException(
    message: String = "It's CRITICAL EXCEPTION",
) : RuntimeException(message)
