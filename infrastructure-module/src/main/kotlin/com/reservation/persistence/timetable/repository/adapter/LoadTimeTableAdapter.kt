package com.reservation.persistence.timetable.repository.adapter

import com.reservation.persistence.timetable.entity.TimeTableEntity
import com.reservation.persistence.timetable.repository.jpa.TimeTableJpaRepository
import com.reservation.timetable.TimeTable
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.port.output.LoadBookableTimeTables.LoadBookableTimeTablesInquiry
import org.springframework.stereotype.Component

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
