package com.reservation.timetable.port.output

interface CheckTimeTableFairLock {
    fun isHeldByCurrentThread(name: String): Boolean
}
