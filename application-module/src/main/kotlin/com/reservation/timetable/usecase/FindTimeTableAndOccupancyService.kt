package com.reservation.timetable.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.timetable.port.input.FindTimeTableAndOccupancyUseCase
import com.reservation.timetable.port.input.query.response.FindTimeTableAndOccupancyQueryResult
import com.reservation.timetable.port.output.FindTimeTableAndOccupancy
import com.reservation.timetable.port.output.FindTimeTableAndOccupancy.FindTimeTableAndOccupancyInquiry
import com.reservation.timetable.port.output.FindTimeTableAndOccupancy.FindTimeTableAndOccupancyResult
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindTimeTableAndOccupancyService(
    private val findTimeTableAndOccupancy: FindTimeTableAndOccupancy,
) : FindTimeTableAndOccupancyUseCase {
    @Transactional(readOnly = true)
    override fun execute(
        timeTableId: String,
        timeTableOccupancyId: String,
    ): FindTimeTableAndOccupancyQueryResult {
        val inquiry = FindTimeTableAndOccupancyInquiry(timeTableId, timeTableOccupancyId)
        return findTimeTableAndOccupancy.query(inquiry)
            ?.toQuery()
            ?: throw NoSuchPersistedElementException()
    }

    fun FindTimeTableAndOccupancyResult.toQuery() =
        FindTimeTableAndOccupancyQueryResult(
            timeTableId = timeTableId,
            restaurantId = restaurantId,
            date = date,
            day = day,
            startTime = startTime,
            endTime = endTime,
            tableNumber = tableNumber,
            tableSize = tableSize,
            timeTableOccupancyId = timeTableOccupancyId,
            userId = userId,
            occupiedDatetime = occupiedDatetime,
        )
}
