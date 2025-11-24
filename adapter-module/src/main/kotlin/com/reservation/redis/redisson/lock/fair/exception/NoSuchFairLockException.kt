package com.reservation.redis.redisson.lock.fair.exception

import com.reservation.exceptions.InvalidSituationException

class NoSuchFairLockException(
    message: String = "The lock has not been found.",
) : InvalidSituationException(message)
