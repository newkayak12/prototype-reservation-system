package com.reservation.persistence.timetable.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.persistence.timetable.entity.QTimeTableEntity.timeTableEntity
import java.time.LocalDate

object TimeTableDateQuerySpec {
    fun timeTableDateEq(date: LocalDate): BooleanExpression =
        timeTableEntity.date.eq(date)
}
