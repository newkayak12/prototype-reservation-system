package com.reservation.common.exceptions

import com.reservation.exceptions.ClientException

class WrongLoginIdOrPasswordException : ClientException("Id or Password maybe wrong.")
