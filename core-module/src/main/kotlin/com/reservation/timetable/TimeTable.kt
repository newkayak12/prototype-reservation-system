package com.reservation.timetable

import com.reservation.enumeration.TableStatus
import com.reservation.enumeration.TableStatus.EMPTY
import com.reservation.enumeration.TableStatus.OCCUPIED
import com.reservation.timetable.snapshot.TimeTableSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Suppress("LongParameterList")
class TimeTable(
    val id: String? = null,
    val restaurantId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val tableNumber: Int,
    val tableSize: Int,
    private var tableStatus: TableStatus,
    private var timetableOccupancy: TimetableOccupancy?,
) {
    val getStatus: TableStatus
        get() = tableStatus
    val getOccupancy: TimetableOccupancy?
        get() = timetableOccupancy

    private fun isOccupied(): Boolean = timetableOccupancy != null

    fun attachOccupied(userId: String) {
        if (isOccupied()) return
        tableStatus = OCCUPIED
        timetableOccupancy = TimetableOccupancy(timeTableId = id!!, userId = userId)
    }

    fun detachOccupied() {
        if (!isOccupied()) return
        tableStatus = EMPTY
        timetableOccupancy?.unoccupied()
    }

    fun toSnapshot(): TimeTableSnapshot {
        return TimeTableSnapshot(
            id = id,
            restaurantId = restaurantId,
            date = date,
            day = day,
            startTime = startTime,
            endTime = endTime,
            tableNumber = tableNumber,
            tableSize = tableSize,
            tableStatus = tableStatus,
            timetableOccupancy = timetableOccupancy?.toSnapshot(),
        )
    }
}
