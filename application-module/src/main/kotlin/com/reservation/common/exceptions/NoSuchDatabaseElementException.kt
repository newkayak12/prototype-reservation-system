package com.reservation.common.exceptions

import com.reservation.exceptions.ClientException

class NoSuchDatabaseElementException : ClientException("There is no element.")
