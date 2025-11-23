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
        DISTINCT TimeTableEntity.identifier,
        TimeTableEntity.restaurantId,
        TimeTableEntity.date,
        TimeTableEntity.day,
        TimeTableEntity.startTime,
        TimeTableEntity.endTime,
        TimeTableEntity.tableNumber,
        TimeTableEntity.tableSize,
        TimeTableEntity.tableStatus,
        TimeTableEntity.timeTableConfirmStatus
        FROM TimeTableEntity
        WHERE TimeTableEntity.restaurantId = :restaurantId
        AND TimeTableEntity.date = :date
        AND TimeTableEntity.startTime = :startTime
        AND TimeTableEntity.tableStatus = 'EMPTY'
        AND TimeTableEntity.timeTableConfirmStatus = 'NOT_CONFIRMED'
        AND NOT EXISTS (
            SELECT 1 FROM TimeTableOccupancyEntity
            WHERE TimeTableOccupancyEntity.timeTable.identifier = TimeTableEntity.identifier
            AND TimeTableOccupancyEntity.occupiedStatus = 'OCCUPIED'
        );
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
