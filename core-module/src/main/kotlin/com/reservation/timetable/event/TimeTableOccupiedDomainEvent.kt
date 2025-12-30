package com.reservation.timetable.event

data class TimeTableOccupiedDomainEvent(
    val timeTableId: String,
    val timeTableOccupancyId: String,
)
