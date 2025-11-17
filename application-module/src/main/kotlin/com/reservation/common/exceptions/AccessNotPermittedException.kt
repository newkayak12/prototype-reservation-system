package com.reservation.common.exceptions

import com.reservation.exceptions.ClientException

class AccessNotPermittedException : ClientException("Access to this feature is not permitted")
