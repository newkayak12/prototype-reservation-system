package com.reservation.event.timetable.occupancy

import com.reservation.event.abstractEvent.TimeTableOccupancyEvent

data class TimeTableOccupiedEvent(
    val timeTableId: String,
    val timeTableOccupancyId: String,
) : TimeTableOccupancyEvent
