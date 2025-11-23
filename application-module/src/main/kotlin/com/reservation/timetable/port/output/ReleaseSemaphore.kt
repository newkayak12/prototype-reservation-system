package com.reservation.timetable.port.output

interface ReleaseSemaphore {
    fun release(name: String)
}
