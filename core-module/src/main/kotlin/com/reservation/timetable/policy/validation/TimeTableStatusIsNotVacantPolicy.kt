package com.reservation.timetable.policy.validation

import com.reservation.enumeration.TableStatus

class TimeTableStatusIsNotVacantPolicy(
    override val reason: String = "This table is not vacant.",
) : TimeTableStatusPolicy {
    override fun validate(tableStatus: TableStatus): Boolean = tableStatus.isOccupied()
}
