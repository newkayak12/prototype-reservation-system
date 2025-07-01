package com.reservation.user.exceptions

import com.reservation.exceptions.ClientException

class WrongLoginIdOrPasswordException : ClientException("Id or Password maybe wrong.")
