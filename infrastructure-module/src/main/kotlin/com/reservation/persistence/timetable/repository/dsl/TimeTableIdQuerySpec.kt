package com.reservation.persistence.timetable.repository.dsl

import com.reservation.persistence.timetable.entity.QTimeTableEntity.timeTableEntity

object TimeTableIdQuerySpec {
    fun timeTableIdEq(timeTableId: String) = timeTableEntity.identifier.eq(timeTableId)
}
