package com.reservation.exceptions

class AlreadyExpiredException(message: String = "EXPIRED TOKEN!") : UnauthorizedException(message)
