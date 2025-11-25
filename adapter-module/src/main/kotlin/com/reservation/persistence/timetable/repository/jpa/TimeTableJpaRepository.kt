package com.reservation.persistence.timetable.repository.jpa

import com.reservation.persistence.timetable.entity.TimeTableEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.time.LocalTime

interface TimeTableJpaRepository : CrudRepository<TimeTableEntity, String> {
    companion object {
        private const val FIND_BOOKABLE_TIME_STAMP_SQL = """
        SELECT
        timetable.identifier,
        timetable.restaurantId,
        timetable.date,
        timetable.day,
        timetable.startTime,
        timetable.endTime,
        timetable.tableNumber,
        timetable.tableSize,
        timetable.tableStatus,
        timetable.timeTableConfirmStatus
        FROM TimeTableEntity timetable
        WHERE timetable.restaurantId = :restaurantId
        AND timetable.date = :date
        AND timetable.startTime = :startTime
        AND timetable.tableStatus = 'EMPTY'
        AND timetable.timeTableConfirmStatus = 'NOT_CONFIRMED'
        AND NOT EXISTS (
            SELECT 1 FROM TimeTableOccupancyEntity timetableOccupancy
            WHERE timetableOccupancy.timeTable.identifier = timetable.identifier
            AND timetableOccupancy.occupiedStatus = 'OCCUPIED'
        )
        """
    }

    @Query(FIND_BOOKABLE_TIME_STAMP_SQL)
    fun findBookableTimeTable(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
    ): List<TimeTableEntity>

    fun findTimeTableEntityByIdentifierEquals(identifier: String): TimeTableEntity?
}
