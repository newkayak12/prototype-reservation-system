package com.reservation.timetable.port.output

import java.util.concurrent.TimeUnit

interface AcquireTimeTableFairLock {
    fun tryLock(
        name: String,
        waitTime: Long,
        waitTimeUnit: TimeUnit,
    ): Boolean
}
