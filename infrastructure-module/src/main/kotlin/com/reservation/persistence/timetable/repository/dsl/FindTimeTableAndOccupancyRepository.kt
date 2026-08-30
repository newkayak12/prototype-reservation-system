package com.reservation.persistence.timetable.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.enumeration.OccupyStatus.OCCUPIED
import com.reservation.enumeration.TableStatus
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
                timeTableEntity.startTime,
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
                TimeTableStatusQuerySpec.timeTableStatusEq(TableStatus.OCCUPIED),
                // 하류(예약 생성)는 확정 시점에 트리거되므로 CONFIRMED를 보게 된다. 상태를
                // 찍어서 비교하는 대신 "아직 풀리지 않았는가"로 묻는다 — 예전 OCCUPIED 데이터도
                // 같은 조건으로 걸린다.
                TimeTableOccupancyStatusQuerySpec.timeTableOccupancyIsAlive(),
            )
            .fetchOne()
    }
}
