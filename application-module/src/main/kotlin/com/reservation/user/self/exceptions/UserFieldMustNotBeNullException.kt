package com.reservation.user.self.exceptions

import com.reservation.exceptions.InvalidSituationException

class UserFieldMustNotBeNullException(
    message: String = "This field must not be null",
) : InvalidSituationException(message)
