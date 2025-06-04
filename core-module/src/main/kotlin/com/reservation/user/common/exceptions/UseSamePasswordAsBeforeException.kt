package com.reservation.user.common.exceptions

import com.reservation.exceptions.ClientException

class UseSamePasswordAsBeforeException : ClientException("You use the same password as before.")
