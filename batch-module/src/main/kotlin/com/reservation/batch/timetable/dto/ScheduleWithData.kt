package com.reservation.batch.timetable.dto

import com.reservation.persistence.schedule.entity.HolidayEntity
import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.schedule.entity.TableEntity
import com.reservation.persistence.schedule.entity.TimeSpanEntity

data class ScheduleWithData(
    val schedule: ScheduleEntity,
    val holidays: List<HolidayEntity>,
    val tables: List<TableEntity>,
    val timeSpans: List<TimeSpanEntity>,
)
