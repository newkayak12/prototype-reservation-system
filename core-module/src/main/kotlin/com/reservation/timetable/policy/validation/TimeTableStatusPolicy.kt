package com.reservation.timetable.policy.validation

import com.reservation.enumeration.TableStatus

interface TimeTableStatusPolicy {
    val reason: String

    fun validate(tableStatus: TableStatus): Boolean = tableStatus.isOccupied()
}
