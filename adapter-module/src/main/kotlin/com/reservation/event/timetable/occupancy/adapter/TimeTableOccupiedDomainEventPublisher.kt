package com.reservation.event.timetable.occupancy.adapter

import com.reservation.timetable.event.TimeTableOccupiedDomainEvent
import com.reservation.timetable.port.output.DelegateReservation
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class TimeTableOccupiedDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DelegateReservation {
    override fun command(domainEvent: TimeTableOccupiedDomainEvent) {
        applicationEventPublisher.publishEvent(domainEvent)
    }
}
