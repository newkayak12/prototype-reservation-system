package com.reservation.user.exceptions

import com.reservation.exceptions.ClientException

class NoSuchDatabaseElementException : ClientException("There is no element.")
