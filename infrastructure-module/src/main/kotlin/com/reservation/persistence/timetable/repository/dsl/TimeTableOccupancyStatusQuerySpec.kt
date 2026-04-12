package com.reservation.persistence.timetable.repository.dsl

import com.reservation.enumeration.OccupyStatus
import com.reservation.persistence.timetable.entity.QTimeTableOccupancyEntity.timeTableOccupancyEntity

object TimeTableOccupancyStatusQuerySpec {
    fun timeTableOccupancyEq(occupyStatus: OccupyStatus) =
        timeTableOccupancyEntity.occupiedStatus.eq(occupyStatus)
}
