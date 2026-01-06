package com.reservation.persistence.timetable.repository.adapter

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.persistence.timetable.entity.TimeTableEntity
import com.reservation.persistence.timetable.entity.TimeTableOccupancyEntity
import com.reservation.persistence.timetable.repository.jpa.TimeTableJpaRepository
import com.reservation.persistence.timetable.repository.jpa.TimeTableOccupancyJpaRepository
import com.reservation.timetable.port.output.CreateTimeTableOccupancy
import com.reservation.timetable.port.output.CreateTimeTableOccupancy.CreateTimeTableOccupancyInquiry
import com.reservation.timetable.port.output.CreateTimeTableOccupancy.TimetableOccupancyInquiry
import org.springframework.stereotype.Component

@Component
class CreateTimeTableOccupancyAdapter(
    private val timeTableJpaRepository: TimeTableJpaRepository,
    private val timeTableOccupancyJpaRepository: TimeTableOccupancyJpaRepository,
) : CreateTimeTableOccupancy {
    private fun findTimeTableEntity(identifier: String): TimeTableEntity {
        return timeTableJpaRepository.findTimeTableEntityByIdentifierEquals(identifier)
            ?: throw NoSuchPersistedElementException()
    }

    private fun createTimeTableOccupancy(
        inquiry: TimetableOccupancyInquiry,
        timeTableEntity: TimeTableEntity,
    ): TimeTableOccupancyEntity =
        TimeTableOccupancyEntity(timeTable = timeTableEntity, userId = inquiry.userId)

    override fun command(inquiry: CreateTimeTableOccupancyInquiry): String? {
        val timetableEntity =
            findTimeTableEntity(inquiry.id)
                .also { it.modifyTableStatus(inquiry.tableStatus) }

        val timeTableOccupancyEntity =
            createTimeTableOccupancy(
                inquiry.timetableOccupancy,
                timetableEntity,
            )

        return timeTableOccupancyJpaRepository.save(timeTableOccupancyEntity).id
    }
}
