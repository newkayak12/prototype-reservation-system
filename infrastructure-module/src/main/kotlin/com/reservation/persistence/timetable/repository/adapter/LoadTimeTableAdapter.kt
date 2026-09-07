package com.reservation.persistence.timetable.repository.adapter

import com.reservation.persistence.timetable.entity.TimeTableEntity
import com.reservation.persistence.timetable.repository.jpa.TimeTableJpaRepository
import com.reservation.timetable.TimeTable
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.port.output.LoadBookableTimeTables.LoadBookableTimeTablesInquiry
import org.springframework.stereotype.Component

/**
 * 예약 가능한 좌석 목록을 잠그지 않고 읽는다.
 *
 * 예약 요청 경로에서 좌석 카운터에 심을 초기값(= 예약 가능한 행 개수)을 구하는 데 쓴다.
 * **어느 행을 쓸지는 여기서 고르지 않는다** — 그 선택은 소비 시점에 [ClaimTimeTableAdapter]가
 * 잠그면서 한다.
 */
@Component
class LoadTimeTableAdapter(
    private val timeTableJpaRepository: TimeTableJpaRepository,
) : LoadBookableTimeTables {
    override fun query(inquiry: LoadBookableTimeTablesInquiry): List<TimeTable> {
        val entities =
            timeTableJpaRepository.findBookableTimeTable(
                restaurantId = inquiry.restaurantId,
                date = inquiry.date,
                startTime = inquiry.startTime,
            )

        return entities.map { it.toDomainEntity() }
    }

    private fun TimeTableEntity.toDomainEntity() =
        TimeTable(
            id = identifier,
            restaurantId = restaurantId,
            date = date,
            day = day,
            startTime = startTime,
            endTime = endTime,
            tableNumber = tableNumber,
            tableSize = tableSize,
            tableStatus = tableStatus,
            timeTableConfirmStatus = timeTableConfirmStatus,
            timetableOccupancy = null,
        )
}
