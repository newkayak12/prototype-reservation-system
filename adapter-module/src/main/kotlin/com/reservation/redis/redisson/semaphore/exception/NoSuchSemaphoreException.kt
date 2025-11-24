package com.reservation.redis.redisson.semaphore.exception

import com.reservation.exceptions.InvalidSituationException

class NoSuchSemaphoreException(
    message: String = "Semaphore has not been found.",
) : InvalidSituationException(message)
