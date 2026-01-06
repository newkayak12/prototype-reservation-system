package com.reservation.timetable.port.output

import com.reservation.timetable.event.TimeTableOccupiedDomainEvent

interface DelegateReservation {
    fun command(domainEvent: TimeTableOccupiedDomainEvent)
}
