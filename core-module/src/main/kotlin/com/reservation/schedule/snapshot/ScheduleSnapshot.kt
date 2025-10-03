package com.reservation.schedule.snapshot


class ScheduleSnapshot(
    val restaurantId: String,
    val timeSpans: List<TimeSpanSnapshot> = listOf(),
    val holidays: List<HolidaySnapshot> = listOf(),
) {
}
