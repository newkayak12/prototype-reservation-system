package com.reservation.user.common.exceptions

import com.reservation.exceptions.InvalidSituationException

class ResignWithoutIdException : InvalidSituationException(
    "User tries withdraw but, have no id.",
)
