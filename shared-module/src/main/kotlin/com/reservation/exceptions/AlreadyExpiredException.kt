package com.reservation.exceptions

class AlreadyExpiredException(message: String = "EXPIRED TOKEN!") : UnAuthorizedException(message)
