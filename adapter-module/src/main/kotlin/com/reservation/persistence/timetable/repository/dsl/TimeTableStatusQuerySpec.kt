package com.reservation.persistence.timetable.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.enumeration.TableStatus
import com.reservation.persistence.timetable.entity.QTimeTableEntity.timeTableEntity

object TimeTableStatusQuerySpec {
    fun timeTableStatusEq(tableStatus: TableStatus): BooleanExpression =
        timeTableEntity.tableStatus.eq(tableStatus)
}
