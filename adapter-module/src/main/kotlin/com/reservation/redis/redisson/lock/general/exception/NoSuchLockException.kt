package com.reservation.redis.redisson.lock.general.exception

import com.reservation.exceptions.InvalidSituationException

class NoSuchLockException(
    message: String = "The lock has not been found.",
) : InvalidSituationException(message)
