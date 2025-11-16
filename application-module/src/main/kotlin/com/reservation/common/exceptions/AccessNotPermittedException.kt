package com.reservation.common.exceptions

import com.reservation.exceptions.ClientException

class AccessNotPermittedException : ClientException("This feature is not permitted to access")
