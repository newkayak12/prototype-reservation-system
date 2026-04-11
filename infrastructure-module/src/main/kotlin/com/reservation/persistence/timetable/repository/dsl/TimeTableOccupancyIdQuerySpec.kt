package com.reservation.persistence.timetable.repository.dsl

import com.reservation.persistence.timetable.entity.QTimeTableOccupancyEntity.timeTableOccupancyEntity

object TimeTableOccupancyIdQuerySpec {
    fun timeTableOccupancyIdEq(timeTableOccupancyId: String) =
        timeTableOccupancyEntity.identifier.eq(timeTableOccupancyId)
}
