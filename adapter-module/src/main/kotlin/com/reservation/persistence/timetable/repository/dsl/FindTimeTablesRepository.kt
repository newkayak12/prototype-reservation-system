package com.reservation.persistence.timetable.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.timetable.QTimeTableEntity.timeTableEntity
import com.reservation.timetable.port.output.FindTimeTables
import com.reservation.timetable.port.output.FindTimeTables.FindTimeTableInquiry
import com.reservation.timetable.port.output.FindTimeTables.FindTimeTableResult
import org.springframework.stereotype.Component

@Component
class FindTimeTablesRepository(
    private val query: JPAQueryFactory,
) : FindTimeTables {

    override fun query(inquiry: FindTimeTableInquiry): List<FindTimeTableResult> =
        query.select(
            Projections.constructor(
                FindTimeTableResult::class.java,
                timeTableEntity.identifier,
                timeTableEntity.restaurantId,
                timeTableEntity.date,
                timeTableEntity.day,
                timeTableEntity.startTime,
                timeTableEntity.endTime,
                timeTableEntity.tableNumber,
                timeTableEntity.tableSize,
                timeTableEntity.tableStatus
            )
        )
        .where(
            RestaurantQuerySpec.restaurantIdEq(inquiry.restaurantId),
            TimeTableDateQuerySpec.timeTableDateEq(inquiry.date),
            TimeTableStatusQuerySpec.timeTableStatusEq(inquiry.tableStatus)
        )
        .from(timeTableEntity)
        .fetch()
}
