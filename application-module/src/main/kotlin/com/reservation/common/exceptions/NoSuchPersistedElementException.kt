package com.reservation.common.exceptions

import com.reservation.exceptions.ClientException

class NoSuchPersistedElementException : ClientException("There is no element.")
