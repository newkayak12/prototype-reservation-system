package com.reservation.common.exceptions

import com.reservation.exceptions.ClientException

class AlreadyPersistedException : ClientException(
    "This element already exists",
)
