package com.reservation.exceptions

class InvalidTokenException(message: String = "INVALID TOKEN!") : UnAuthorizedException(message)
