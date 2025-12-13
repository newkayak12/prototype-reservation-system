package com.reservation.persistence.timetable.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.timetable.entity.QTimeTableEntity.timeTableEntity
import com.reservation.persistence.timetable.entity.QTimeTableOccupancyEntity.timeTableOccupancyEntity
import com.reservation.timetable.port.output.FindTimeTableAndOccupancy
import com.reservation.timetable.port.output.FindTimeTableAndOccupancy.FindTimeTableAndOccupancyInquiry
import com.reservation.timetable.port.output.FindTimeTableAndOccupancy.FindTimeTableAndOccupancyResult
import org.springframework.stereotype.Component

@Component
class FindTimeTableAndOccupancyRepository(
    private val query: JPAQueryFactory,
) : FindTimeTableAndOccupancy {
    override fun query(
        inquiry: FindTimeTableAndOccupancyInquiry,
    ): FindTimeTableAndOccupancyResult? {
        return query.select(
            Projections.constructor(
                FindTimeTableAndOccupancyResult::class.java,
                Expressions.constant(inquiry.timeTableId),
                timeTableEntity.restaurantId,
                timeTableEntity.date,
                timeTableEntity.day,
                timeTableEntity.endTime,
                timeTableEntity.tableNumber,
                timeTableEntity.tableSize,
                Expressions.constant(inquiry.timeTableOccupancyId),
                timeTableOccupancyEntity.userId,
                timeTableOccupancyEntity.occupiedDatetime,
            ),
        )
            .from(timeTableEntity)
            .join(timeTableOccupancyEntity)
            .on(timeTableEntity.identifier.eq(timeTableOccupancyEntity.timeTable.identifier))
            .where(
                TimeTableIdQuerySpec.timeTableIdEq(inquiry.timeTableId),
                TimeTableOccupancyIdQuerySpec.timeTableOccupancyIdEq(inquiry.timeTableOccupancyId),
            )
            .fetchOne()
    }
}
